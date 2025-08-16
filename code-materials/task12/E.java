
    private synchronized LogEntry readNextEntry() {
        try {
            try {
                while (true) {
                    final String line = in.readLine();
                    if (line == null) {
                        break;
                    }

                    Matcher m = src.timep.matcher(line);
                    if (m.lookingAt()) {
                        if (buf.length() > 0) {
                            final LogEntry e = new Log4JEntry(
                                    src.timestampFromText(dateformat, buf),
                                    src.getServerId(),
                                    buf
                            );
                            buf = line;
                            return e;
                        }
                        buf = line;
                    } else if (buf.length() > 0) {
                        buf += line + "\n";
                    }
                }
            } catch (final EOFException eof) {
                // ignore, we've simply come to the end of the file
            }
            if (buf.length() > 0) {
                final LogEntry e = new Log4JEntry(
                        src.timestampFromText(dateformat, buf),
                        src.getServerId(),
                        buf
                );
                buf = "";
                return e;
            }
        } catch (final Exception e) {
            LOG.error("Error reading next entry in file (" + src.file + "): " + e);
            return null;
        }
        return null;
    }
