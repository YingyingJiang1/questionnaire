
    private static String extractBasicHeader(final String emlContent, final String headerName) {
        if (log.isDebugEnabled()) {
            log.debug("extractBasicHeader() called for header: [{}]", headerName);
        }

        try {
            final String[] lines = emlContent.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                final String line = lines[i];
                final boolean isTargetHeader = line.toLowerCase().startsWith(headerName.toLowerCase());
                if (isTargetHeader) {
                    final StringBuilder value = new StringBuilder(line.substring(headerName.length()).trim());
                    
                    // Handle multi-line headers
                    for (int j = i + 1; j < lines.length; j++) {
                        final String continuationLine = lines[j];
                        final boolean isContinuation = continuationLine.startsWith(" ") || continuationLine.startsWith("\t");
                        if (isContinuation) {
                            value.append(" ").append(continuationLine.trim());
                        } else {
                            break;
                        }
                    }
                    
                    return safeMimeDecode(value.toString());
                }
                
                // Stop processing if we encounter an empty line
                if (line.trim().isEmpty()) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Error extracting header '{}': {}", headerName, e.getMessage());
        }
        return "";
    }
