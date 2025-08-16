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
