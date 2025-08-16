
    private static void handleError(final Context context, final ErrorInfo errorInfo) {
        Throwable throwable = errorInfo.getThrowable();
        if (throwable != null) throwable.printStackTrace();

        if (throwable instanceof ReCaptchaException) {
            Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
            final Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else if (throwable != null && ExceptionUtils.isNetworkRelated(throwable)) {
            Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof AgeRestrictedContentException) {
            Toast.makeText(context, R.string.restricted_video_no_stream, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof GeographicRestrictionException) {
            Toast.makeText(context, R.string.georestricted_content, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof PaidContentException) {
            Toast.makeText(context, R.string.paid_content, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof PrivateContentException) {
            Toast.makeText(context, R.string.private_content, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof SoundCloudGoPlusContentException) {
            Toast.makeText(context, R.string.soundcloud_go_plus_content, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof YoutubeMusicPremiumContentException) {
            Toast.makeText(context, R.string.youtube_music_premium_content, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof ContentNotAvailableException) {
            Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
        } else if (throwable instanceof ContentNotSupportedException) {
            Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show();
        } else {
            ErrorUtil.createNotification(context, errorInfo);
        }

        if (context instanceof RouterActivity) {
            ((RouterActivity) context).finish();
        }
    }
