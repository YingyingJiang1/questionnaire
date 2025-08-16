
private synchronized LogEntry readNextEntry() {
    try {
        try {
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }

                Matcher m = src.timep.matcher(line);
                if (m.lookingAt()) {
                    if (buf.length() > 0) {
                        LogEntry e = new Log4JEntry(
                                src.timestampFromText(dateformat, buf), 
                                src.getServerId(), buf);
                        buf = line;
                        return e;
                    }
                    buf = line;
                } else if (buf.length() > 0) {
                    buf += line + "\n";
                }
            }
        } catch (EOFException eof) {
            // End of file
        }
        if (buf.length() > 0) {
            LogEntry e = new Log4JEntry(
                    src.timestampFromText(dateformat, buf), 
                    src.getServerId(), buf);
            buf = "";
            return e;
        }
    } catch (Exception e) {
        LOG.error("Error reading next entry in file ({}): {}", 
                src.file, e);
        return null;
    }
    return null;
}
