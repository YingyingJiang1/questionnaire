    public synchronized Map<String, Object> getConnectionInfo(boolean brief) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("remote_socket_address", getRemoteSocketAddress());
        info.put("interest_ops", getInterestOps());
        info.put("outstanding_requests", getOutstandingRequests());
        info.put("packets_received", getPacketsReceived());
        info.put("packets_sent", getPacketsSent());
        if (!brief) {
            info.put("session_id", getSessionId());
            info.put("last_operation", getLastOperation());
            info.put("established", getEstablished());
            info.put("session_timeout", getSessionTimeout());
            info.put("last_cxid", getLastCxid());
            info.put("last_zxid", getLastZxid());
            info.put("last_response_time", getLastResponseTime());
            info.put("last_latency", getLastLatency());
            info.put("min_latency", getMinLatency());
            info.put("avg_latency", getAvgLatency());
            info.put("max_latency", getMaxLatency());
        }
        return info;
    }


    public static void closeSock(SocketChannel sock) {
        if (!sock.isOpen()) {
            return;
        }

        try {
            /*
             * The following sequence of code is stupid! You would think that
             * only sock.close() is needed, but alas, it doesn't work that way.
             * If you just do sock.close() there are cases where the socket
             * doesn't actually close...
             */
            sock.socket().shutdownOutput();
        } catch (IOException e) {
            // This is a relatively common exception that we can't avoid
            LOG.debug("ignoring exception during output shutdown", e);
        }
        try {
            sock.socket().shutdownInput();
        } catch (IOException e) {
            // This is a relatively common exception that we can't avoid
            LOG.debug("ignoring exception during input shutdown", e);
        }
        try {
            sock.socket().close();
        } catch (IOException e) {
            LOG.debug("ignoring exception during socket close", e);
        }
        try {
            sock.close();
        } catch (IOException e) {
            LOG.debug("ignoring exception during socketchannel close", e);
        }
    }


    public String getStatus(String name, long timeout) throws KeeperException, InterruptedException {
        Stat stat = new Stat();
        byte[] data = null;
        long endTime = Time.currentElapsedTime() + timeout;
        KeeperException lastException = null;
        for(int i = 0; i < maxTries && endTime > Time.currentElapsedTime(); i++) {
            try {
                data = zk.getData(reportsNode + '/' + name, false, stat);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Got Data: " + ((data == null) ? "null" : new String(data)));
                }
                lastException = null;
                break;
            } catch(ConnectionLossException e) {
                lastException = e;
            } catch(NoNodeException e) {
                final Object eventObj = new Object();
                synchronized(eventObj) {
                    // wait for the node to appear
                    Stat eStat = zk.exists(reportsNode + '/' + name, new Watcher() {
                        public void process(WatchedEvent event) {
                            synchronized(eventObj) {
                                eventObj.notifyAll();
                            }
                        }});
                    if (eStat == null) {
                        eventObj.wait(endTime - Time.currentElapsedTime());
                    }
                }
                lastException = e;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return new String(data);
    }


    public synchronized void dumpConnectionInfo(PrintWriter pwriter, boolean brief) {
        pwriter.print(" ");
        pwriter.print(getRemoteSocketAddress());
        pwriter.print("[");
        int interestOps = getInterestOps();
        pwriter.print(interestOps == 0 ? "0" : Integer.toHexString(interestOps));
        pwriter.print("](queued=");
        pwriter.print(getOutstandingRequests());
        pwriter.print(",recved=");
        pwriter.print(getPacketsReceived());
        pwriter.print(",sent=");
        pwriter.print(getPacketsSent());

        if (!brief) {
            long sessionId = getSessionId();
            if (sessionId != 0) {
                pwriter.print(",sid=0x");
                pwriter.print(Long.toHexString(sessionId));
                pwriter.print(",lop=");
                pwriter.print(getLastOperation());
                pwriter.print(",est=");
                pwriter.print(getEstablished().getTime());
                pwriter.print(",to=");
                pwriter.print(getSessionTimeout());
                long lastCxid = getLastCxid();
                if (lastCxid >= 0) {
                    pwriter.print(",lcxid=0x");
                    pwriter.print(Long.toHexString(lastCxid));
                }
                pwriter.print(",lzxid=0x");
                pwriter.print(Long.toHexString(getLastZxid()));
                pwriter.print(",lresp=");
                pwriter.print(getLastResponseTime());
                pwriter.print(",llat=");
                pwriter.print(getLastLatency());
                pwriter.print(",minlat=");
                pwriter.print(getMinLatency());
                pwriter.print(",avglat=");
                pwriter.print(getAvgLatency());
                pwriter.print(",maxlat=");
                pwriter.print(getMaxLatency());
            }
        }
        pwriter.print(")");
    }


    public static void main(String[] args) throws IOException {	
	MergedLogSource source = new MergedLogSource(args);

	PrintStream ps_ms = new PrintStream(new BufferedOutputStream(new FileOutputStream("throughput-ms.out")));
	PrintStream ps_sec = new PrintStream(new BufferedOutputStream(new FileOutputStream("throughput-sec.out")));
	PrintStream ps_min = new PrintStream(new BufferedOutputStream(new FileOutputStream("throughput-min.out")));
	PrintStream ps_hour = new PrintStream(new BufferedOutputStream(new FileOutputStream("throughput-hour.out")));
	LogIterator iter;
	
	System.out.println(source);
	iter = source.iterator();
	long currentms = 0;
	long currentsec = 0;
	long currentmin = 0;
	long currenthour = 0;
	Set<Long> zxids_ms = new HashSet<Long>();
	long zxid_sec = 0;
	long zxid_min = 0;
	long zxid_hour = 0;

	while (iter.hasNext()) {
	    LogEntry e = iter.next();
	    TransactionEntry cxn = (TransactionEntry)e;
	    
	    long ms = cxn.getTimestamp();
	    long sec = ms/MS_PER_SEC;
	    long min = ms/MS_PER_MIN;
	    long hour = ms/MS_PER_HOUR;

	    if (currentms != ms && currentms != 0) {
		ps_ms.println("" + currentms + " " + zxids_ms.size());

		zxid_sec += zxids_ms.size();
		zxid_min += zxids_ms.size();
		zxid_hour += zxids_ms.size();
		zxids_ms.clear();
	    }

	    if (currentsec != sec && currentsec != 0) {
		ps_sec.println("" + currentsec*MS_PER_SEC + " " + zxid_sec);

		zxid_sec = 0;
	    }

	    if (currentmin != min && currentmin != 0) {
		ps_min.println("" + currentmin*MS_PER_MIN + " " + zxid_min);
		
		zxid_min = 0;
	    }

	    if (currenthour != hour && currenthour != 0) {
		ps_hour.println("" + currenthour*MS_PER_HOUR + " " + zxid_hour);
		
		zxid_hour = 0;
	    }

	    currentms = ms;
	    currentsec = sec;
	    currentmin = min;
	    currenthour = hour;

	    zxids_ms.add(cxn.getZxid());
	}

	iter.close();
	ps_ms.close();
	ps_sec.close();
	ps_min.close();
	ps_hour.close();
    }


