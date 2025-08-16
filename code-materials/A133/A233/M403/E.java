
private static void sanitizeHtmlFilesInZip(Path zipFilePath, boolean disableSanitize, TempFileManager tempFileManager) throws IOException {
    try (TempDirectory tempUnzippedDir = new TempDirectory(tempFileManager)) {
        try (ZipInputStream zipIn = ZipSecurity.createHardenedInputStream(new ByteArrayInputStream(Files.readAllBytes(zipFilePath)))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = tempUnzippedDir.getPath().resolve(sanitizeZipFilename(entry.getName()));
                if (!entry.isDirectory()) {
                    Files.createDirectories(filePath.getParent());
                    if (entry.getName().toLowerCase().endsWith(".html") || entry.getName().toLowerCase().endsWith(".htm")) {
                        String content = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                        String sanitizedContent = sanitizeHtmlContent(content, disableSanitize);
                        Files.write(filePath, sanitizedContent.getBytes(StandardCharsets.UTF_8));
                    } else {
                        Files.copy(zipIn, filePath);
                    }
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        zipDirectory(tempUnzippedDir.getPath(), zipFilePath);
    }
}
