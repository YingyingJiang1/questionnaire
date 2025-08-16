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
