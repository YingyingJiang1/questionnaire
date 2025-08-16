    private JsonNode fetchMachinesForLicense(String licenseKey, String licenseId) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        BASE_URL
                                                + "/"
                                                + ACCOUNT_ID
                                                + "/licenses/"
                                                + licenseId
                                                + "/machines"))
                        .header("Content-Type", "application/vnd.api+json")
                        .header("Accept", "application/vnd.api+json")
                        .header("Authorization", "License " + licenseKey)
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("fetchMachinesForLicense Response body: {}", response.body());

        if (response.statusCode() == 200) {
            return objectMapper.readTree(response.body());
        } else {
            log.error(
                    "Error fetching machines for license. Status code: {}, error: {}",
                    response.statusCode(),
                    response.body());
            return null;
        }
    }


    private void redactAreas(
            List<RedactionArea> redactionAreas, PDDocument document, PDPageTree allPages)
            throws IOException {
        // Group redaction areas by page
        Map<Integer, List<RedactionArea>> redactionsByPage = new HashMap<>();

        // Process and validate each redaction area
        for (RedactionArea redactionArea : redactionAreas) {
            if (redactionArea.getPage() == null
                    || redactionArea.getPage() <= 0
                    || redactionArea.getHeight() == null
                    || redactionArea.getHeight() <= 0.0D
                    || redactionArea.getWidth() == null
                    || redactionArea.getWidth() <= 0.0D) continue;

            // Group by page number
            redactionsByPage
                    .computeIfAbsent(redactionArea.getPage(), k -> new ArrayList<>())
                    .add(redactionArea);
        }

        // Process each page only once
        for (Map.Entry<Integer, List<RedactionArea>> entry : redactionsByPage.entrySet()) {
            Integer pageNumber = entry.getKey();
            List<RedactionArea> areasForPage = entry.getValue();

            if (pageNumber > allPages.getCount()) {
                continue; // Skip if page number is out of bounds
            }

            PDPage page = allPages.get(pageNumber - 1);
            PDRectangle box = page.getBBox();

            // Create only one content stream per page
            PDPageContentStream contentStream =
                    new PDPageContentStream(
                            document, page, PDPageContentStream.AppendMode.APPEND, true, true);

            // Process all redactions for this page
            for (RedactionArea redactionArea : areasForPage) {
                Color redactColor = decodeOrDefault(redactionArea.getColor(), Color.BLACK);
                contentStream.setNonStrokingColor(redactColor);

                float x = redactionArea.getX().floatValue();
                float y = redactionArea.getY().floatValue();
                float width = redactionArea.getWidth().floatValue();
                float height = redactionArea.getHeight().floatValue();

                contentStream.addRect(x, box.getHeight() - y - height, width, height);
                contentStream.fill();
            }

            contentStream.close();
        }
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


