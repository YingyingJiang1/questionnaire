    @PostMapping(consumes = "multipart/form-data", value = "/file/pdf")
    @Operation(
            summary = "Convert a file to a PDF using LibreOffice",
            description =
                    "This endpoint converts a given file to a PDF using LibreOffice API  Input:ANY"
                            + " Output:PDF Type:SISO")
    public ResponseEntity<byte[]> processFileToPDF(@ModelAttribute GeneralFile generalFile)
            throws Exception {
        MultipartFile inputFile = generalFile.getFileInput();
        // unused but can start server instance if startup time is to long
        // LibreOfficeListener.getInstance().start();
        File file = null;
        try {
            file = convertToPdf(inputFile);

            PDDocument doc = pdfDocumentFactory.load(file);
            return WebResponseUtils.pdfDocToWebResponse(
                    doc,
                    Filenames.toSimpleFileName(inputFile.getOriginalFilename())
                                    .replaceFirst("[.][^.]+$", "")
                            + "_convertedToPDF.pdf");
        } finally {
            if (file != null) file.delete();
        }
    }
