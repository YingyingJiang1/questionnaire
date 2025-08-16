    public void evictSessions() {
        long now = System.currentTimeMillis();
        List<Session> toClose = new ArrayList<Session>();
        for (Session session : sessions.values()) {
            // do not close if there is still job running,
            // e.g. trace command might wait for a long time before condition is met
            //TODO check background job size
            if (now - session.getLastAccessTime() > sessionTimeoutMillis && session.getForegroundJob() == null) {
                toClose.add(session);
            }
            evictConsumers(session);
        }
        for (Session session : toClose) {
            //interrupt foreground job
            Job job = session.getForegroundJob();
            if (job != null) {
                job.interrupt();
            }
            long timeOutInMinutes = sessionTimeoutMillis / 1000 / 60;
            String reason = "session is inactive for " + timeOutInMinutes + " min(s).";
            SharingResultDistributor resultDistributor = session.getResultDistributor();
            if (resultDistributor != null) {
                resultDistributor.appendResult(new MessageModel(reason));
            }
            this.removeSession(session.getSessionId());
            logger.info("Removing inactive session: {}, last access time: {}", session.getSessionId(), session.getLastAccessTime());
        }
    }
