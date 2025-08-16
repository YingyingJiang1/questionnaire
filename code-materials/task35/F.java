
    private static void sanitizeHtmlFilesInZip(
            Path zipFilePath, boolean disableSanitize, TempFileManager tempFileManager)
            throws IOException {
        TempDirectory tempUnzippedDir = null;
        try {
            tempUnzippedDir = new TempDirectory(tempFileManager);
            
            ZipInputStream zipIn = null;
            try {
                zipIn = ZipSecurity.createHardenedInputStream(
                        new ByteArrayInputStream(Files.readAllBytes(zipFilePath)));
                ZipEntry entry = zipIn.getNextEntry();
                while (entry != null) {
                    Path filePath = tempUnzippedDir.getPath().resolve(sanitizeZipFilename(entry.getName()));
                    if (!entry.isDirectory()) {
                        Files.createDirectories(filePath.getParent());
                        if (entry.getName().toLowerCase().endsWith(".html") 
                                || entry.getName().toLowerCase().endsWith(".htm")) {
                            String content = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                            String sanitizedContent = sanitizeHtmlContent(content, disableSanitize);
                            Files.write(
                                    filePath, sanitizedContent.getBytes(StandardCharsets.UTF_8));
                        } else {
                            Files.copy(zipIn, filePath);
                        }
                    }
                    zipIn.closeEntry();
                    entry = zipIn.getNextEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (zipIn != null) {
                    try {
                        zipIn.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            // Repack the sanitized files
            zipDirectory(tempUnzippedDir.getPath(), zipFilePath);
        } finally {
            if (tempUnzippedDir != null) {
                try {
                    tempUnzippedDir.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
