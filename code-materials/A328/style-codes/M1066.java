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
