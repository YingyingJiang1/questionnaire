    private static void handleError(final Context context, final ErrorInfo errorInfo) {
        if (errorInfo.getThrowable() != null) {
            errorInfo.getThrowable().printStackTrace();
        }

        if (errorInfo.getThrowable() instanceof ReCaptchaException) {
            Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
            // Starting ReCaptcha Challenge Activity
            final Intent intent = new Intent(context, ReCaptchaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else if (errorInfo.getThrowable() != null
                && ExceptionUtils.isNetworkRelated(errorInfo.getThrowable())) {
            Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof AgeRestrictedContentException) {
            Toast.makeText(context, R.string.restricted_video_no_stream,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof GeographicRestrictionException) {
            Toast.makeText(context, R.string.georestricted_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof PaidContentException) {
            Toast.makeText(context, R.string.paid_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof PrivateContentException) {
            Toast.makeText(context, R.string.private_content, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof SoundCloudGoPlusContentException) {
            Toast.makeText(context, R.string.soundcloud_go_plus_content,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof YoutubeMusicPremiumContentException) {
            Toast.makeText(context, R.string.youtube_music_premium_content,
                    Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof ContentNotAvailableException) {
            Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
        } else if (errorInfo.getThrowable() instanceof ContentNotSupportedException) {
            Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show();
        } else {
            ErrorUtil.createNotification(context, errorInfo);
        }

        if (context instanceof RouterActivity) {
            ((RouterActivity) context).finish();
        }
    }
