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
