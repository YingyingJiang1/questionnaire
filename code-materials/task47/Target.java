    static BufferedImage convertColorType(BufferedImage sourceImage, String colorType) {
        BufferedImage convertedImage;
        switch (colorType) {
            case "greyscale":
                convertedImage =
                        new BufferedImage(
                                sourceImage.getWidth(),
                                sourceImage.getHeight(),
                                BufferedImage.TYPE_BYTE_GRAY);
                convertedImage.getGraphics().drawImage(sourceImage, 0, 0, null);
                break;
            case "blackwhite":
                convertedImage =
                        new BufferedImage(
                                sourceImage.getWidth(),
                                sourceImage.getHeight(),
                                BufferedImage.TYPE_BYTE_BINARY);
                convertedImage.getGraphics().drawImage(sourceImage, 0, 0, null);
                break;
            default: // full color
                convertedImage = sourceImage;
                break;
        }
        return convertedImage;
    }


    private boolean verifyJWTLicense(String licenseKey, LicenseContext context) {
        try {
            log.info("Verifying ED25519_SIGN format license key");

            // Remove the "key/" prefix
            String licenseData = licenseKey.substring(JWT_PREFIX.length());

            // Split into payload and signature
            String[] parts = licenseData.split("\\.", 2);
            if (parts.length != 2) {
                log.error(
                        "Invalid ED25519_SIGN license format. Expected format:"
                                + " key/payload.signature");
                return false;
            }

            String encodedPayload = parts[0];
            String encodedSignature = parts[1];

            // Verify signature
            boolean isSignatureValid = verifyJWTSignature(encodedPayload, encodedSignature);
            if (!isSignatureValid) {
                log.error("ED25519_SIGN license signature is invalid");
                return false;
            }

            log.info("ED25519_SIGN license signature is valid");

            // Decode and process payload - first convert from URL-safe base64 if needed
            String base64Payload = encodedPayload.replace('-', '+').replace('_', '/');
            byte[] payloadBytes = Base64.getDecoder().decode(base64Payload);
            String payload = new String(payloadBytes);

            // Process the license payload
            boolean isValid = processJWTLicensePayload(payload, context);

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying ED25519_SIGN license: {}", e.getMessage());
            return false;
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


    @PostMapping(value = "/split-pdf-by-chapters", consumes = "multipart/form-data")
    @Operation(
            summary = "Split PDFs by Chapters",
            description = "Splits a PDF into chapters and returns a ZIP file.")
    public ResponseEntity<byte[]> splitPdf(@ModelAttribute SplitPdfByChaptersRequest request)
            throws Exception {
        MultipartFile file = request.getFileInput();
        PDDocument sourceDocument = null;
        Path zipFile = null;

        try {
            boolean includeMetadata = Boolean.TRUE.equals(request.getIncludeMetadata());
            Integer bookmarkLevel =
                    request.getBookmarkLevel(); // levels start from 0 (top most bookmarks)
            if (bookmarkLevel < 0) {
                throw ExceptionUtils.createIllegalArgumentException(
                        "error.invalidArgument", "Invalid argument: {0}", "bookmark level");
            }
            sourceDocument = pdfDocumentFactory.load(file);

            PDDocumentOutline outline = sourceDocument.getDocumentCatalog().getDocumentOutline();

            if (outline == null) {
                log.warn("No outline found for {}", file.getOriginalFilename());
                throw ExceptionUtils.createIllegalArgumentException(
                        "error.pdfBookmarksNotFound", "No PDF bookmarks/outline found in document");
            }
            List<Bookmark> bookmarks = new ArrayList<>();
            try {
                bookmarks =
                        extractOutlineItems(
                                sourceDocument,
                                outline.getFirstChild(),
                                bookmarks,
                                outline.getFirstChild().getNextSibling(),
                                0,
                                bookmarkLevel);
                // to handle last page edge case
                bookmarks.get(bookmarks.size() - 1).setEndPage(sourceDocument.getNumberOfPages());
                Bookmark lastBookmark = bookmarks.get(bookmarks.size() - 1);

            } catch (Exception e) {
                ExceptionUtils.logException("outline extraction", e);
                return ResponseEntity.internalServerError()
                        .body("Unable to extract outline items".getBytes());
            }

            boolean allowDuplicates = Boolean.TRUE.equals(request.getAllowDuplicates());
            if (!allowDuplicates) {
                /*
                duplicates are generated when multiple bookmarks correspond to the same page,
                if the user doesn't want duplicates mergeBookmarksThatCorrespondToSamePage() method will merge the titles of all
                the bookmarks that correspond to the same page, and treat them as a single bookmark
                */
                bookmarks = mergeBookmarksThatCorrespondToSamePage(bookmarks);
            }
            for (Bookmark bookmark : bookmarks) {
                log.info(
                        "{}::::{} to {}",
                        bookmark.getTitle(),
                        bookmark.getStartPage(),
                        bookmark.getEndPage());
            }
            List<ByteArrayOutputStream> splitDocumentsBoas =
                    getSplitDocumentsBoas(sourceDocument, bookmarks, includeMetadata);

            zipFile = createZipFile(bookmarks, splitDocumentsBoas);

            byte[] data = Files.readAllBytes(zipFile);
            Files.deleteIfExists(zipFile);

            String filename =
                    Filenames.toSimpleFileName(file.getOriginalFilename())
                            .replaceFirst("[.][^.]+$", "");
            sourceDocument.close();
            return WebResponseUtils.bytesToWebResponse(
                    data, filename + ".zip", MediaType.APPLICATION_OCTET_STREAM);
        } finally {
            try {
                if (sourceDocument != null) {
                    sourceDocument.close();
                }
                if (zipFile != null) {
                    Files.deleteIfExists(zipFile);
                }
            } catch (Exception e) {
                log.error("Error while cleaning up resources", e);
            }
        }
    }


