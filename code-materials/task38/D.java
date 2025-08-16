
    private void showError(DownloadMission mission, UserAction action, @StringRes int reason) {
        final StringBuilder request = new StringBuilder(256);
        request.append(mission.source);

        request.append(" [");
        if (mission.recoveryInfo != null) {
            for (MissionRecoveryInfo recovery : mission.recoveryInfo)
                request.append(' ')
                        .append(recovery.toString())
                        .append(' ');
        }
        request.append("]");

        String service;
        try {
            service = NewPipe.getServiceByUrl(mission.source).getServiceInfo().getName();
        } catch (Exception e) {
            service = ErrorInfo.SERVICE_NONE;
        }

        ErrorUtil.createNotification(mContext,
                new ErrorInfo(ErrorInfo.Companion.throwableToStringList(mission.errObject), action,
                        service, request.toString(), reason));
    }
