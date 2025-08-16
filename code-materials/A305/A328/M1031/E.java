
void establishConnection(final int threadId, final HttpURLConnection conn)
        throws IOException, HttpError {
    final int statusCode = conn.getResponseCode();

    if (DEBUG) {
        Log.d(TAG, threadId + ":[request]  Range=" + conn.getRequestProperty("Range"));
        Log.d(TAG, threadId + ":[response] Code=" + statusCode);
        Log.d(TAG, threadId + ":[response] Content-Length=" + conn.getContentLength());
        Log.d(TAG, threadId + ":[response] Content-Range=" + conn.getHeaderField("Content-Range"));
    }

    switch (statusCode) {
        case 204:
        case 205:
        case 207:
            throw new HttpError(statusCode);
        case 416:
            // let the download thread handle this error
            return;
        default:
            if (statusCode < 200 || statusCode > 299) {
                throw new HttpError(statusCode);
            }
    }
}
