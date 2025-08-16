    private Trun parseTrun() throws IOException {
        final Trun obj = new Trun();
        obj.bFlags = stream.readInt();
        obj.entryCount = stream.readInt(); // unsigned int

        obj.entriesRowSize = 0;
        if (hasFlag(obj.bFlags, 0x0100)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0200)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0400)) {
            obj.entriesRowSize += 4;
        }
        if (hasFlag(obj.bFlags, 0x0800)) {
            obj.entriesRowSize += 4;
        }
        obj.bEntries = new byte[obj.entriesRowSize * obj.entryCount];

        if (hasFlag(obj.bFlags, 0x0001)) {
            obj.dataOffset = stream.readInt();
        }
        if (hasFlag(obj.bFlags, 0x0004)) {
            obj.bFirstSampleFlags = stream.readInt();
        }

        stream.read(obj.bEntries);

        for (int i = 0; i < obj.entryCount; i++) {
            final TrunEntry entry = obj.getEntry(i);
            if (hasFlag(obj.bFlags, 0x0100)) {
                obj.chunkDuration += entry.sampleDuration;
            }
            if (hasFlag(obj.bFlags, 0x0200)) {
                obj.chunkSize += entry.sampleSize;
            }
            if (hasFlag(obj.bFlags, 0x0800)) {
                if (!hasFlag(obj.bFlags, 0x0100)) {
                    obj.chunkDuration += entry.sampleCompositionTimeOffset;
                }
            }
        }

        return obj;
    }


        private void showPopupMenu() {
            retry.setVisible(false);
            cancel.setVisible(false);
            start.setVisible(false);
            pause.setVisible(false);
            open.setVisible(false);
            queue.setVisible(false);
            showError.setVisible(false);
            delete.setVisible(false);
            source.setVisible(false);
            checksum.setVisible(false);

            DownloadMission mission = item.mission instanceof DownloadMission ? (DownloadMission) item.mission : null;

            if (mission != null) {
                if (mission.hasInvalidStorage()) {
                    retry.setVisible(true);
                    delete.setVisible(true);
                    showError.setVisible(true);
                } else if (mission.isPsRunning()) {
                    switch (mission.errCode) {
                        case ERROR_INSUFFICIENT_STORAGE:
                        case ERROR_POSTPROCESSING_HOLD:
                            retry.setVisible(true);
                            cancel.setVisible(true);
                            showError.setVisible(true);
                            break;
                    }
                } else {
                    if (mission.running) {
                        pause.setVisible(true);
                    } else {
                        if (mission.errCode != ERROR_NOTHING) {
                            showError.setVisible(true);
                        }

                        queue.setChecked(mission.enqueued);

                        delete.setVisible(true);

                        boolean flag = !mission.isPsFailed() && mission.urls.length > 0;
                        start.setVisible(flag);
                        queue.setVisible(flag);
                    }
                }
            } else {
                open.setVisible(true);
                delete.setVisible(true);
                checksum.setVisible(true);
            }

            if (item.mission.source != null && !item.mission.source.isEmpty()) {
                source.setVisible(true);
            }

            popupMenu.show();
        }


    private void loadPendingMissions(Context ctx) {
        File[] subs = mPendingMissionsDir.listFiles();

        if (subs == null) {
            Log.e(TAG, "listFiles() returned null");
            return;
        }
        if (subs.length < 1) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Loading pending downloads from directory: " + mPendingMissionsDir.getAbsolutePath());
        }

        File tempDir = pickAvailableTemporalDir(ctx);
        Log.i(TAG, "using '" + tempDir + "' as temporal directory");

        for (File sub : subs) {
            if (!sub.isFile()) continue;
            if (sub.getName().equals(".tmp")) continue;

            DownloadMission mis = Utility.readFromFile(sub);
            if (mis == null || mis.isFinished() || mis.hasInvalidStorage()) {
                //noinspection ResultOfMethodCallIgnored
                sub.delete();
                continue;
            }

            mis.threads = new Thread[0];

            boolean exists;
            try {
                mis.storage = StoredFileHelper.deserialize(mis.storage, ctx);
                exists = !mis.storage.isInvalid() && mis.storage.existsAsFile();
            } catch (Exception ex) {
                Log.e(TAG, "Failed to load the file source of " + mis.storage.toString(), ex);
                mis.storage.invalidate();
                exists = false;
            }

            if (mis.isPsRunning()) {
                if (mis.psAlgorithm.worksOnSameFile) {
                    // Incomplete post-processing results in a corrupted download file
                    // because the selected algorithm works on the same file to save space.
                    // the file will be deleted if the storage API
                    // is Java IO (avoid showing the "Save as..." dialog)
                    if (exists && mis.storage.isDirect() && !mis.storage.delete())
                        Log.w(TAG, "Unable to delete incomplete download file: " + sub.getPath());
                }

                mis.psState = 0;
                mis.errCode = DownloadMission.ERROR_POSTPROCESSING_STOPPED;
            } else if (!exists) {
                tryRecover(mis);

                // the progress is lost, reset mission state
                if (mis.isInitialized())
                    mis.resetState(true, true, DownloadMission.ERROR_PROGRESS_LOST);
            }

            if (mis.psAlgorithm != null) {
                mis.psAlgorithm.cleanupTemporalDir();
                mis.psAlgorithm.setTemporalDir(tempDir);
            }

            mis.metadata = sub;
            mis.maxRetry = mPrefMaxRetry;
            mis.mHandler = mHandler;

            mMissionsPending.add(mis);
        }

        if (mMissionsPending.size() > 1)
            Collections.sort(mMissionsPending, Comparator.comparingLong(Mission::getTimestamp));
    }


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


