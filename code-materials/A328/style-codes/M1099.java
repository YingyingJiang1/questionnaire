    @SuppressLint("DefaultLocale")
    private void updateProgress(ViewHolderItem h) {
        if (h == null || h.item == null || h.item.mission instanceof FinishedMission) return;

        DownloadMission mission = (DownloadMission) h.item.mission;
        double done = mission.done;
        long length = mission.getLength();
        long now = System.currentTimeMillis();
        boolean hasError = mission.errCode != ERROR_NOTHING;

        // hide on error
        // show if current resource length is not fetched
        // show if length is unknown
        h.progress.setMarquee(mission.isRecovering() || !hasError && (!mission.isInitialized() || mission.unknownLength));

        double progress;
        if (mission.unknownLength) {
            progress = Double.NaN;
            h.progress.setProgress(0.0f);
        } else {
            progress = done / length;
        }

        if (hasError) {
            h.progress.setProgress(isNotFinite(progress) ? 1d : progress);
            h.status.setText(R.string.msg_error);
        } else if (isNotFinite(progress)) {
            h.status.setText(UNDEFINED_PROGRESS);
        } else {
            h.status.setText(String.format("%.2f%%", progress * 100));
            h.progress.setProgress(progress);
        }

        @StringRes int state;
        String sizeStr = Utility.formatBytes(length).concat("  ");

        if (mission.isPsFailed() || mission.errCode == ERROR_POSTPROCESSING_HOLD) {
            h.size.setText(sizeStr);
            return;
        } else if (!mission.running) {
            state = mission.enqueued ? R.string.queued : R.string.paused;
        } else if (mission.isPsRunning()) {
            state = R.string.post_processing;
        } else if (mission.isRecovering()) {
            state = R.string.recovering;
        } else {
            state = 0;
        }

        if (state != 0) {
            // update state without download speed
            h.size.setText(sizeStr.concat("(").concat(mContext.getString(state)).concat(")"));
            h.resetSpeedMeasure();
            return;
        }

        if (h.lastTimestamp < 0) {
            h.size.setText(sizeStr);
            h.lastTimestamp = now;
            h.lastDone = done;
            return;
        }

        long deltaTime = now - h.lastTimestamp;
        double deltaDone = done - h.lastDone;

        if (h.lastDone > done) {
            h.lastDone = done;
            h.size.setText(sizeStr);
            return;
        }

        if (deltaDone > 0 && deltaTime > 0) {
            float speed = (float) ((deltaDone * 1000d) / deltaTime);
            float averageSpeed = speed;

            if (h.lastSpeedIdx < 0) {
                Arrays.fill(h.lastSpeed, speed);
                h.lastSpeedIdx = 0;
            } else {
                for (int i = 0; i < h.lastSpeed.length; i++) {
                    averageSpeed += h.lastSpeed[i];
                }
                averageSpeed /= h.lastSpeed.length + 1.0f;
            }

            String speedStr = Utility.formatSpeed(averageSpeed);
            String etaStr;

            if (mission.unknownLength) {
                etaStr = "";
            } else {
                long eta = (long) Math.ceil((length - done) / averageSpeed);
                etaStr = Utility.formatBytes((long) done) + "/" + Utility.stringifySeconds(eta) + "  ";
            }

            h.size.setText(sizeStr.concat(etaStr).concat(speedStr));

            h.lastTimestamp = now;
            h.lastDone = done;
            h.lastSpeed[h.lastSpeedIdx++] = speed;

            if (h.lastSpeedIdx >= h.lastSpeed.length) h.lastSpeedIdx = 0;
        }
    }
