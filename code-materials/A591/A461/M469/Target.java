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


    String handleRequest(JsonRequest request) throws Exception {
	

	long starttime = 0;
	long endtime = 0;
	long period = 0;
	FilterOp fo = null;

	starttime = request.getNumber("start", 0);
	endtime = request.getNumber("end", 0);
	period = request.getNumber("period", 0);
	String filterstr = request.getString("filter", "");

	if (filterstr.length() > 0) {
	    fo = new FilterParser(filterstr).parse();
	}
	
	if (starttime == 0) { starttime = source.getStartTime(); }
	if (endtime == 0) { 
	    if (period > 0) {
		endtime = starttime + period;
	    } else {
		endtime = starttime + DEFAULT_PERIOD; 
	    }
	}

	if (LOG.isDebugEnabled()) {
	    LOG.debug("handle(start= " + starttime + ", end=" + endtime + ", period=" + period + ")");
	}
	
	LogIterator iterator = (fo != null) ? 
	    source.iterator(starttime, endtime, fo) : source.iterator(starttime, endtime);
	return new JsonGenerator(iterator).toString();
    }


	synchronized public long size() throws IOException {
	    if (LOG.isTraceEnabled()) {
		LOG.trace("size() called");
	    }

	    if (this.endtime >= src.getEndTime()) {
		return src.size() - skippedAtStart;
	    }
	    
	    long pos = in.getPosition();
	    
	    if (LOG.isTraceEnabled()) {
		LOG.trace("saved pos () = " + pos);
	    }
	    
	    LogEntry e;
	  
	    LogSkipList.Mark lastseg = src.getSkipList().findMarkBefore(this.endtime);
	    in.seek(lastseg.getBytes());
	    buf = "";  // clear the buf so we don't get something we read before we sought
	    // number of entries skipped to get to the end of the iterator, less the number skipped to get to the start
	    long count = lastseg.getEntriesSkipped() - skippedAtStart; 

	    while ((e = readNextEntry()) != null) {
		if (LOG.isTraceEnabled()) {
		    //LOG.trace(e);
		}
		if (e.getTimestamp() > this.endtime) {
		    break;
		}
		count++;
	    }
	    in.seek(pos);
	    buf = "";

	    if (LOG.isTraceEnabled()) {
		LOG.trace("size() = " + count);
	    }
	    
	    return count;
	}


    public boolean matches(LogEntry entry) throws FilterException {
	Arg first = args.get(0);
	
	if (first != null) {
	    FilterOp.ArgType type = first.getType();
	    if (type == FilterOp.ArgType.SYMBOL) {
		String key = (String)first.getValue();
		Object v = entry.getAttribute(key);
		if (v instanceof String) {
		    type = FilterOp.ArgType.STRING;
		} else if (v instanceof Double || v instanceof Long || v instanceof Integer || v instanceof Short) {
		    type = FilterOp.ArgType.NUMBER;
		} else {
		    throw new FilterException("LessThanOp: Invalid argument, first argument resolves to neither a String nor a Number");
		}
	    }
	    
	    Object last = null;
	    for (Arg a : args) {
		Object v = a.getValue();
		if (a.getType() == FilterOp.ArgType.SYMBOL) {
		    String key = (String)a.getValue();
		    v = entry.getAttribute(key);
		}

		if (last != null) {
		    if (type == FilterOp.ArgType.STRING) {
			if (((String)last).compareTo((String)v) >= 0) {
			    return false;
			}
		    } else if (type == FilterOp.ArgType.NUMBER) {
			if (((Number)last).doubleValue() >= ((Number)v).doubleValue()) {
			    return false;
			}
		    }
		}
		last = v;
	    }
	    return true;
	} else { 
	    return true; 
	}
    }


		public void run()
		{
			try
			{
				boolean animateFromBottom = true;
				GraphicsEnvironment ge = GraphicsEnvironment
						.getLocalGraphicsEnvironment();
				Rectangle screenRect = ge.getMaximumWindowBounds();

				int screenHeight = (int) screenRect.height;

				int startYPosition;
				int stopYPosition;

				if ( screenRect.y > 0 )
				{
				  animateFromBottom = false; // Animate from top!
				}

				maxToasterInSceen = screenHeight / toasterHeight;


				int posx = (int) screenRect.width - toasterWidth - 1;

				toaster.setLocation(posx, screenHeight);
				toaster.setVisible(true);
				if ( useAlwaysOnTop )
				{
				  toaster.setAlwaysOnTop(true);
				}

				if ( animateFromBottom )
				{
					startYPosition = screenHeight;
					stopYPosition = startYPosition - toasterHeight - 1;
					if ( currentNumberOfToaster > 0 )
					{
						stopYPosition = stopYPosition - ( maxToaster % maxToasterInSceen * toasterHeight );
					}
					else
					{
						maxToaster = 0;
					}
				}
				else
				{
					startYPosition = screenRect.y - toasterHeight;
					stopYPosition = screenRect.y;

					if ( currentNumberOfToaster > 0 )
					{
						stopYPosition = stopYPosition + ( maxToaster % maxToasterInSceen * toasterHeight );
					}
					else
					{
						maxToaster = 0;
					}
				}

				currentNumberOfToaster++;
				maxToaster++;


				animateVertically( posx, startYPosition, stopYPosition );
				Thread.sleep(displayTime);
				animateVertically( posx, stopYPosition, startYPosition );

				currentNumberOfToaster--;
				toaster.setVisible(false);
				toaster.dispose();
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}


