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
