    public void dump(PrintWriter pwriter) {
        pwriter.print("Sets (");
        pwriter.print(expiryMap.size());
        pwriter.print(")/(");
        pwriter.print(elemMap.size());
        pwriter.println("):");
        ArrayList<Long> keys = new ArrayList<>(expiryMap.keySet());
        Collections.sort(keys);
        for (long time : keys) {
            Set<E> set = expiryMap.get(time);
            if (set != null) {
                pwriter.print(set.size());
                pwriter.print(" expire at ");
                pwriter.print(Time.elapsedTimeToDate(time));
                pwriter.println(":");
                for (E elem : set) {
                    pwriter.print("\t");
                    pwriter.println(elem.toString());
                }
            }
        }
    }


   private void extractEndpoints() {
       int count = 1;
       while (true) {
           String e = cfg.getProperty(
                   String.format("rest.endpoint.%d", count), null);
           if (e == null) {
               break;
           }

           String[] parts = e.split(";");
           if (parts.length != 2) {
               count++;
               continue;
           }
           Endpoint point = new Endpoint(parts[0], parts[1]);
           
           String c = cfg.getProperty(String.format(
                   "rest.endpoint.%d.http.auth", count), "");
           point.setCredentials(c);
           
           String digest = cfg.getProperty(String.format(
                   "rest.endpoint.%d.zk.digest", count), "");
           point.setZooKeeperAuthInfo(digest);

           endpoints.add(point);
           count++;
       }
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


