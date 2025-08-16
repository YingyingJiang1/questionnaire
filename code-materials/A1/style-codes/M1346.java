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
