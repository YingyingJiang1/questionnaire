    private static String extractBasicHeader(String emlContent, String headerName) {
        try {
            String[] lines = emlContent.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase().startsWith(headerName.toLowerCase())) {
                    StringBuilder value =
                            new StringBuilder(line.substring(headerName.length()).trim());
                    // Handle multi-line headers
                    for (int j = i + 1; j < lines.length; j++) {
                        if (lines[j].startsWith(" ") || lines[j].startsWith("\t")) {
                            value.append(" ").append(lines[j].trim());
                        } else {
                            break;
                        }
                    }
                    // Apply MIME header decoding
                    return safeMimeDecode(value.toString());
                }
                if (line.trim().isEmpty()) break;
            }
        } catch (RuntimeException e) {
            log.warn("Error extracting header '{}': {}", headerName, e.getMessage());
        }
        return "";
    }
