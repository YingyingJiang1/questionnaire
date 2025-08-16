  private static String extractBasicHeader(String emlContent, String headerName) {
    try {
      String[] lines = emlContent.split("\r?\n");
      int i = 0; 
      while (i < lines.length) { 
        String line = lines[i];
        if (line.toLowerCase().startsWith(headerName.toLowerCase())) {

          StringBuilder value = new StringBuilder(line.substring(headerName.length()).trim());

          // Handle multi-line headers
          int j = i + 1; 
          while (j < lines.length) { 
            if (lines[j].startsWith(" ") || lines[j].startsWith("\t")) {
              value.append(" ").append(lines[j].trim());
            } else {
              break;
            }
            j++;
          } 
          return safeMimeDecode(value.toString());
        }

        if (line.trim().isEmpty()) {
          break;
        }
        i++;
      } 

    } catch (RuntimeException e) {

      log.warn("Error extracting header '{}': {}", headerName, e.getMessage());

    }

    return "";

  }
