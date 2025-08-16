
    @SuppressWarnings("SynchronizeOnNonFinalField")
    private synchronized LogEntry readNextEntry() {
        try {
            for (;;) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }

                Matcher m = src.timep.matcher(line);
                if (m.lookingAt()) {
                    if (buf.length() > 0) {
                        LogEntry e = new Log4JEntry(src.timestampFromText(dateformat, buf), src.getServerId(), buf);
                        buf = line;
                        return e;
                    }
                    buf = line;
                } else if (buf.length() > 0) {
                    buf += line + "\n";
                }
            }
            if (buf.length() > 0) {
                LogEntry e = new Log4JEntry(src.timestampFromText(dateformat, buf), src.getServerId(), buf);
                buf = "";
                return e;
            }
        } catch (EOFException eof) {
            // ignore, we've simply come to the end of the file
        } catch (Exception e) {
            LOG.error("Error reading next entry in file (" + src.file + "): " + e);
            return null;
        }
        return null;
    }

