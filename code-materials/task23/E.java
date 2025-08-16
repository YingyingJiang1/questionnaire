
@Override
int process(SharpStream out, SharpStream... sources) throws IOException {
    // check if the subtitle is already in srt and copy, this should never happen
    String format = getArgumentAt(0, null);
    boolean ignoreEmptyFrames = getArgumentAt(1, "true").equals("true");

    if (format == null || format.equals("ttml")) {
        SrtFromTtmlWriter writer = new SrtFromTtmlWriter(out, ignoreEmptyFrames);

        try {
            writer.build(sources[0]);
        } catch (Exception err) {
            Log.e(TAG, "subtitle parse failed", err);
            return (err instanceof IOException) ? 1 : 8;
        }

        return OK_RESULT;
    } else if (format.equals("srt")) {
        byte[] buffer = new byte[8 * 1024];
        int read;
        while ((read = sources[0].read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
        return OK_RESULT;
    }

    throw new UnsupportedOperationException(
            "Can't convert this subtitle, unimplemented format: " + format);
}
