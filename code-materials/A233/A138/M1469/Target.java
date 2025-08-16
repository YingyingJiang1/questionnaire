    private Path createTempFile(String prefix) throws IOException {
        // Check if TempFileManager is available in the application context
        try {
            TempFileManager tempFileManager =
                    ApplicationContextProvider.getBean(TempFileManager.class);
            if (tempFileManager != null) {
                // Use TempFileManager to create and register the temp file
                File file = tempFileManager.createTempFile(".tmp");
                log.debug("Created and registered temp file via TempFileManager: {}", file);
                return file.toPath();
            }
        } catch (Exception e) {
            log.debug("TempFileManager not available, falling back to standard temp file creation");
        }

        // Fallback to standard temp file creation
        Path file = Files.createTempFile(prefix + tempCounter.incrementAndGet() + "-", ".tmp");
        log.debug("Created temp file: {}", file);

        // Try to register the file with a static registry if possible
        try {
            TempFileRegistry registry = ApplicationContextProvider.getBean(TempFileRegistry.class);
            if (registry != null) {
                registry.register(file);
                log.debug("Registered fallback temp file with registry: {}", file);
            }
        } catch (Exception e) {
            log.debug("Could not register fallback temp file with registry: {}", file);
        }

        return file;
    }


    private ObjectNode generatePDFSummaryData(PDDocument document) {
        ObjectNode summaryData = objectMapper.createObjectNode();

        // Check if encrypted
        if (document.isEncrypted()) {
            summaryData.put("encrypted", true);
        }

        // Check permissions
        AccessPermission ap = document.getCurrentAccessPermission();
        ArrayNode restrictedPermissions = objectMapper.createArrayNode();

        if (!ap.canAssembleDocument()) restrictedPermissions.add("document assembly");
        if (!ap.canExtractContent()) restrictedPermissions.add("content extraction");
        if (!ap.canExtractForAccessibility()) restrictedPermissions.add("accessibility extraction");
        if (!ap.canFillInForm()) restrictedPermissions.add("form filling");
        if (!ap.canModify()) restrictedPermissions.add("modification");
        if (!ap.canModifyAnnotations()) restrictedPermissions.add("annotation modification");
        if (!ap.canPrint()) restrictedPermissions.add("printing");

        if (restrictedPermissions.size() > 0) {
            summaryData.set("restrictedPermissions", restrictedPermissions);
            summaryData.put("restrictedPermissionsCount", restrictedPermissions.size());
        }

        // Check standard compliance
        if (checkForStandard(document, "PDF/A")) {
            summaryData.put("standardCompliance", "PDF/A");
            summaryData.put("standardPurpose", "long-term archiving");
        } else if (checkForStandard(document, "PDF/X")) {
            summaryData.put("standardCompliance", "PDF/X");
            summaryData.put("standardPurpose", "graphic exchange");
        } else if (checkForStandard(document, "PDF/UA")) {
            summaryData.put("standardCompliance", "PDF/UA");
            summaryData.put("standardPurpose", "universal accessibility");
        } else if (checkForStandard(document, "PDF/E")) {
            summaryData.put("standardCompliance", "PDF/E");
            summaryData.put("standardPurpose", "engineering workflows");
        } else if (checkForStandard(document, "PDF/VT")) {
            summaryData.put("standardCompliance", "PDF/VT");
            summaryData.put("standardPurpose", "variable and transactional printing");
        }

        return summaryData;
    }


    public ResponseEntity<String> printFile(@ModelAttribute PrintFileRequest request)
            throws IOException {
        MultipartFile file = request.getFileInput();
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && (originalFilename.contains("..") || Paths.get(originalFilename).isAbsolute())) {
            throw new IOException("Invalid file path detected: " + originalFilename);
        }
        String printerName = request.getPrinterName();
        String contentType = file.getContentType();
        try {
            // Find matching printer
            PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
            PrintService selectedService =
                    Arrays.stream(services)
                            .filter(
                                    service ->
                                            service.getName().toLowerCase().contains(printerName))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "No matching printer found"));

            log.info("Selected Printer: " + selectedService.getName());

            if ("application/pdf".equals(contentType)) {
                PDDocument document = Loader.loadPDF(file.getBytes());
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(selectedService);
                job.setPageable(new PDFPageable(document));
                job.print();
                document.close();
            } else if (contentType.startsWith("image/")) {
                BufferedImage image = ImageIO.read(file.getInputStream());
                PrinterJob job = PrinterJob.getPrinterJob();
                job.setPrintService(selectedService);
                job.setPrintable(
                        new Printable() {
                            public int print(
                                    Graphics graphics, PageFormat pageFormat, int pageIndex)
                                    throws PrinterException {
                                if (pageIndex != 0) {
                                    return NO_SUCH_PAGE;
                                }
                                Graphics2D g2d = (Graphics2D) graphics;
                                g2d.translate(
                                        pageFormat.getImageableX(), pageFormat.getImageableY());
                                g2d.drawImage(
                                        image,
                                        0,
                                        0,
                                        (int) pageFormat.getImageableWidth(),
                                        (int) pageFormat.getImageableHeight(),
                                        null);
                                return PAGE_EXISTS;
                            }
                        });
                job.print();
            }
            return new ResponseEntity<>(
                    "File printed successfully to " + selectedService.getName(), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("Failed to print: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    private void applyGhostscriptCompression(
            OptimizePdfRequest request, int optimizeLevel, Path currentFile, List<Path> tempFiles)
            throws IOException {

        long preGsSize = Files.size(currentFile);
        log.info("Pre-Ghostscript file size: {}", GeneralUtils.formatBytes(preGsSize));

        // Create output file for Ghostscript
        Path gsOutputFile = Files.createTempFile("gs_output_", ".pdf");
        tempFiles.add(gsOutputFile);

        // Build Ghostscript command based on optimization level
        List<String> command = new ArrayList<>();
        command.add("gs");
        command.add("-sDEVICE=pdfwrite");
        command.add("-dCompatibilityLevel=1.5");
        command.add("-dNOPAUSE");
        command.add("-dQUIET");
        command.add("-dBATCH");

        // Map optimization levels to Ghostscript settings
        switch (optimizeLevel) {
            case 1:
                command.add("-dPDFSETTINGS=/prepress");
                break;
            case 2:
                command.add("-dPDFSETTINGS=/printer");
                break;
            case 3:
                command.add("-dPDFSETTINGS=/ebook");
                break;
            case 4:
            case 5:
                command.add("-dPDFSETTINGS=/screen");
                break;
            case 6:
            case 7:
                command.add("-dPDFSETTINGS=/screen");
                command.add("-dColorImageResolution=150");
                command.add("-dGrayImageResolution=150");
                command.add("-dMonoImageResolution=300");
                break;
            case 8:
            case 9:
                command.add("-dPDFSETTINGS=/screen");
                command.add("-dColorImageResolution=100");
                command.add("-dGrayImageResolution=100");
                command.add("-dMonoImageResolution=200");
                break;
            case 10:
                command.add("-dPDFSETTINGS=/screen");
                command.add("-dColorImageResolution=72");
                command.add("-dGrayImageResolution=72");
                command.add("-dMonoImageResolution=150");
                break;
            default:
                command.add("-dPDFSETTINGS=/screen");
                break;
        }

        command.add("-sOutputFile=" + gsOutputFile.toString());
        command.add(currentFile.toString());

        ProcessExecutorResult returnCode = null;
        try {
            returnCode =
                    ProcessExecutor.getInstance(ProcessExecutor.Processes.GHOSTSCRIPT)
                            .runCommandWithOutputHandling(command);

            if (returnCode.getRc() == 0) {
                // Update current file to the Ghostscript output
                Files.copy(gsOutputFile, currentFile, StandardCopyOption.REPLACE_EXISTING);

                long postGsSize = Files.size(currentFile);
                double gsReduction = 100.0 - ((postGsSize * 100.0) / preGsSize);
                log.info(
                        "Post-Ghostscript file size: {} (reduced by {}%)",
                        GeneralUtils.formatBytes(postGsSize), String.format("%.1f", gsReduction));
            } else {
                log.warn("Ghostscript compression failed with return code: {}", returnCode.getRc());
                throw new IOException("Ghostscript compression failed");
            }

        } catch (Exception e) {
            log.warn("Ghostscript compression failed, will fallback to other methods", e);
            throw new IOException("Ghostscript compression failed", e);
        }
    }


