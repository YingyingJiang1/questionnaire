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
