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
