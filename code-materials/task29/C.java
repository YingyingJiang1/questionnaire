
    void startMission(DownloadMission mission) {
    synchronized(this) {
                           mission.timestamp = System.currentTimeMillis();
                           mission.mHandler = mHandler;
                           mission.maxRetry = mPrefMaxRetry;

            // create metadata file
                           while (true) {
                           mission.metadata = new File(mPendingMissionsDir, String.valueOf(mission.timestamp));
                           if (!mission.metadata.isFile()&& !mission.metadata.exists()) {
                               try
                               {
                                   if (!mission.metadata.createNewFile()) throw new RuntimeException("Cant create download metadata file");
                               } catch (IOException e)
                               {
                                   throw new RuntimeException(e);
                               }

                               break;
                           }
                           mission.timestamp = System.currentTimeMillis();
                           }
                           mSelfMissionsControl = true;
                           mMissionsPending.add(mission);

            // Before continue, save the metadata in case the internet connection is not available
                           Utility.writeToFile(mission.metadata, mission);
                           if (mission.storage == null) {
                // noting to do here
                               mission.errCode = DownloadMission.ERROR_FILE_CREATION;
                               if (mission.errObject != null) mission.errObject = new IOException("DownloadMission.storage == NULL");
                               return;
                           }

                           boolean start = !mPrefQueueLimit || getRunningMissionsCount() < 1;

                           if (canDownloadInCurrentNetwork()&&
                                   start) {
                               mission.start();
                           }
                       }
    }

