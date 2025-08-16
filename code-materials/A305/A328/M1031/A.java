    void establishConnection(int threadId, HttpURLConnection conn) throws IOException, HttpError {
        int statusCode = conn.getResponseCode();

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
                return;// let the download thread handle this error
            default:
                if (statusCode < 200 || statusCode > 299) {
                    throw new HttpError(statusCode);
                }
        }

    }
