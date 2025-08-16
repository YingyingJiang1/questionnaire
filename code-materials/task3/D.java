
@PostMapping(consumes = "multipart/form-data", value = "/file/pdf")
@Operation(
        summary = "Convert file to PDF using LibreOffice",
        description = "Converts input file to PDF. Input:ANY Output:PDF Type:SISO")
public ResponseEntity<byte[]> processFileToPDF(@ModelAttribute GeneralFile generalFile) throws Exception {
    MultipartFile inputFile = generalFile.getFileInput();
    File file = null;
    try {
        file = convertToPdf(inputFile);
        PDDocument doc = pdfDocumentFactory.load(file);
        return WebResponseUtils.pdfDocToWebResponse(
                doc,
                Filenames.toSimpleFileName(inputFile.getOriginalFilename())
                        .replaceFirst("[.][^.]+$", "") + "_convertedToPDF.pdf");
    } finally {
        if (file != null) file.delete();
    }
}
