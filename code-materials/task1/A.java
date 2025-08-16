    @Override
    public Map.Entry<T, ProfilingInfo> build(Object data) {
      List list = (List) data;
      if (list == null || list.isEmpty()) return null;

      if (list.get(0) instanceof KeyValue) { // RESP3
        Object resultsData = null, profileData = null;

        for (KeyValue keyValue : (List<KeyValue>) data) {
          String keyStr = BuilderFactory.STRING.build(keyValue.getKey());
          switch (keyStr) {
            case PROFILE_STR_REDIS7:
            case PROFILE_STR_REDIS8:
              profileData = keyValue.getValue();
              break;
            case RESULTS_STR_REDIS7:
              resultsData = data;
              break;
            case RESULTS_STR_REDIS8:
              resultsData = keyValue.getValue();
              break;
          }
        }

        assert resultsData != null : "Could not detect Results data.";
        assert profileData != null : "Could not detect Profile data.";
        return KeyValue.of(resultsBuilder.build(resultsData),
                ProfilingInfo.PROFILING_INFO_BUILDER.build(profileData));
      }

      return KeyValue.of(resultsBuilder.build(list.get(0)),
          ProfilingInfo.PROFILING_INFO_BUILDER.build(list.get(1)));
    }
