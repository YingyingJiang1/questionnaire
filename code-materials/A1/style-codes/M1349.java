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
