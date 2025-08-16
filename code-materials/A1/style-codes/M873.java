  @Override
  public void addParams(CommandArguments args) {

    if (fromTimestamp == null) {
      args.add(MINUS);
    } else {
      args.add(toByteArray(fromTimestamp));
    }

    if (toTimestamp == null) {
      args.add(PLUS);
    } else {
      args.add(toByteArray(toTimestamp));
    }

    if (latest) {
      args.add(LATEST);
    }

    if (filterByTimestamps != null) {
      args.add(FILTER_BY_TS);
      for (long ts : filterByTimestamps) {
        args.add(toByteArray(ts));
      }
    }

    if (filterByValues != null) {
      args.add(FILTER_BY_VALUE);
      for (double value : filterByValues) {
        args.add(toByteArray(value));
      }
    }

    if (count != null) {
      args.add(COUNT).add(toByteArray(count));
    }

    if (aggregationType != null) {

      if (align != null) {
        args.add(ALIGN).add(align);
      }

      args.add(AGGREGATION).add(aggregationType).add(toByteArray(bucketDuration));

      if (bucketTimestamp != null) {
        args.add(BUCKETTIMESTAMP).add(bucketTimestamp);
      }

      if (empty) {
        args.add(EMPTY);
      }
    }
  }
