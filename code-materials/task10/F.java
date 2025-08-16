
    void establishConnection(final int threadId, final HttpURLConnection conn)
            throws IOException, HttpError {
        if (DEBUG) {
            Log.d(TAG, threadId + ":[request]  Range=" + conn.getRequestProperty("Range"));
            Log.d(TAG, threadId + ":[response] Code=" + conn.getResponseCode());
            Log.d(TAG, threadId + ":[response] Content-Length=" + conn.getContentLength());
            Log.d(TAG, threadId + ":[response] Content-Range=" + conn.getHeaderField("Content-Range"));
        }

        final int statusCode = conn.getResponseCode();

        switch (statusCode) {
            case 204:
            case 205:
            case 207:
                throw new HttpError(statusCode);
            case 416:
                return; // let the download thread handle this error
            default:
                if (statusCode < 200 || statusCode > 299) {
                    throw new HttpError(statusCode);
                }
                break;
        }
    }
