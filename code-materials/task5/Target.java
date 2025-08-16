    @Override
    public Map<String, Object> build(Object data) {
      if (data == null) return null;
      final List<Object> list = (List<Object>) data;
      if (list.isEmpty()) return Collections.emptyMap();

      if (list.get(0) instanceof KeyValue) {
        final Map<String, Object> map = new HashMap<>(list.size(), 1f);
        final Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
          KeyValue kv = (KeyValue) iterator.next();
          map.put(STRING.build(kv.getKey()), ENCODED_OBJECT.build(kv.getValue()));
        }
        return map;
      } else {
        final Map<String, Object> map = new HashMap<>(list.size() / 2, 1f);
        final Iterator iterator = list.iterator();
        while (iterator.hasNext()) {
          map.put(STRING.build(iterator.next()), ENCODED_OBJECT.build(iterator.next()));
        }
        return map;
      }
    }


  @Override
  public Connection getConnection() {
    // In antirez's redis-rb-cluster implementation, getRandomConnection always return
    // valid connection (able to ping-pong) or exception if all connections are invalid

    List<ConnectionPool> pools = cache.getShuffledNodesPool();

    JedisException suppressed = null;
    for (ConnectionPool pool : pools) {
      Connection jedis = null;
      try {
        jedis = pool.getResource();
        if (jedis == null) {
          continue;
        }

        jedis.ping();
        return jedis;

      } catch (JedisException ex) {
        if (suppressed == null) { // remembering first suppressed exception
          suppressed = ex;
        }
        if (jedis != null) {
          jedis.close();
        }
      }
    }

    JedisClusterOperationException noReachableNode = new JedisClusterOperationException("No reachable node in cluster.");
    if (suppressed != null) {
      noReachableNode.addSuppressed(suppressed);
    }
    throw noReachableNode;
  }


    @Override
    public Map<String, Map<String, Double>> build(Object data) {
      List rawDataList = (List) data;
      if (rawDataList.isEmpty()) return Collections.emptyMap();

      if (rawDataList.get(0) instanceof KeyValue) {
        KeyValue rawData = (KeyValue) rawDataList.get(0);
        String header = STRING.build(rawData.getKey());
        if (!RESULTS.equals(header)) {
          throw new IllegalStateException("Unrecognized header: " + header);
        }

        return ((List<KeyValue>) rawData.getValue()).stream().collect(Collectors.toMap(
            rawTerm -> STRING.build(rawTerm.getKey()),
            rawTerm -> ((List<List<KeyValue>>) rawTerm.getValue()).stream()
                .collect(Collectors.toMap(entry -> STRING.build(entry.get(0).getKey()),
                      entry -> BuilderFactory.DOUBLE.build(entry.get(0).getValue()))),
            (x, y) -> x, LinkedHashMap::new));
      }

      Map<String, Map<String, Double>> returnTerms = new LinkedHashMap<>(rawDataList.size());

      for (Object rawData : rawDataList) {
        List<Object> rawElements = (List<Object>) rawData;

        String header = STRING.build(rawElements.get(0));
        if (!TERM.equals(header)) {
          throw new IllegalStateException("Unrecognized header: " + header);
        }
        String term = STRING.build(rawElements.get(1));

        List<List<Object>> list = (List<List<Object>>) rawElements.get(2);
        Map<String, Double> entries = new LinkedHashMap<>(list.size());
        list.forEach(entry -> entries.put(STRING.build(entry.get(1)), BuilderFactory.DOUBLE.build(entry.get(0))));

        returnTerms.put(term, entries);
      }
      return returnTerms;
    }


  private void process() {

    do {
      Object reply = authenticator.client.getUnflushedObject();

      if (reply instanceof List) {
        List<Object> listReply = (List<Object>) reply;
        final Object firstObj = listReply.get(0);
        if (!(firstObj instanceof byte[])) {
          throw new JedisException("Unknown message type: " + firstObj);
        }
        final byte[] resp = (byte[]) firstObj;
        if (Arrays.equals(SSUBSCRIBE.getRaw(), resp)) {
          subscribedChannels = ((Long) listReply.get(2)).intValue();
          final byte[] bchannel = (byte[]) listReply.get(1);
          final T enchannel = (bchannel == null) ? null : encode(bchannel);
          onSSubscribe(enchannel, subscribedChannels);
        } else if (Arrays.equals(SUNSUBSCRIBE.getRaw(), resp)) {
          subscribedChannels = ((Long) listReply.get(2)).intValue();
          final byte[] bchannel = (byte[]) listReply.get(1);
          final T enchannel = (bchannel == null) ? null : encode(bchannel);
          onSUnsubscribe(enchannel, subscribedChannels);
        } else if (Arrays.equals(SMESSAGE.getRaw(), resp)) {
          final byte[] bchannel = (byte[]) listReply.get(1);
          final byte[] bmesg = (byte[]) listReply.get(2);
          final T enchannel = (bchannel == null) ? null : encode(bchannel);
          final T enmesg = (bmesg == null) ? null : encode(bmesg);
          onSMessage(enchannel, enmesg);
        } else {
          throw new JedisException("Unknown message type: " + firstObj);
        }
      } else if (reply instanceof byte[]) {
        Consumer<Object> resultHandler = authenticator.resultHandler.poll();
        if (resultHandler == null) {
          throw new JedisException("Unexpected message : " + SafeEncoder.encode((byte[]) reply));
        }
        resultHandler.accept(reply);
      } else {
        throw new JedisException("Unknown message type: " + reply);
      }
    } while (!Thread.currentThread().isInterrupted() && isSubscribed());

//    /* Invalidate instance since this thread is no longer listening */
//    this.client = null;
  }


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


  @Override
  public void addParams(CommandArguments args) {

    if (dataType != null) {
      args.add(ON).add(dataType);
    }

    if (prefix != null) {
      args.add(PREFIX).add(prefix.size()).addObjects(prefix);
    }

    if (filter != null) {
      args.add(FILTER).add(filter);
    }

    if (language != null) {
      args.add(LANGUAGE).add(language);
    }
    if (languageField != null) {
      args.add(LANGUAGE_FIELD).add(languageField);
    }

    if (score != null) {
      args.add(SCORE).add(score);
    }
    if (scoreField != null) {
      args.add(SCORE_FIELD).add(scoreField);
    }

    if (maxTextFields) {
      args.add(MAXTEXTFIELDS);
    }

    if (noOffsets) {
      args.add(NOOFFSETS);
    }

    if (temporary != null) {
      args.add(TEMPORARY).add(temporary);
    }

    if (noHL) {
      args.add(NOHL);
    }

    if (noFields) {
      args.add(NOFIELDS);
    }

    if (noFreqs) {
      args.add(NOFREQS);
    }

    if (stopwords != null) {
      args.add(STOPWORDS).add(stopwords.size());
      stopwords.forEach(w -> args.add(w));
    }

    if (skipInitialScan) {
      args.add(SKIPINITIALSCAN);
    }
  }


