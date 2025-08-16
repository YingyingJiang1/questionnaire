    private void buildCaptionMenu(@NonNull final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_CAPTION);

        captionPopupMenu.setOnDismissListener(this);

        // Add option for turning off caption
        final MenuItem captionOffItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            final int textRendererIndex = player.getCaptionRendererIndex();
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                player.getTrackSelector().setParameters(player.getTrackSelector()
                        .buildUponParameters().setRendererDisabled(textRendererIndex, true));
            }
            player.getPrefs().edit()
                    .remove(context.getString(R.string.caption_user_set_key)).apply();
            return true;
        });

        // Add all available captions
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            final MenuItem captionItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                    i + 1, Menu.NONE, captionLanguage);
            captionItem.setOnMenuItemClickListener(menuItem -> {
                final int textRendererIndex = player.getCaptionRendererIndex();
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    // DefaultTrackSelector will select for text tracks in the following order.
                    // When multiple tracks share the same rank, a random track will be chosen.
                    // 1. ANY track exactly matching preferred language name
                    // 2. ANY track exactly matching preferred language stem
                    // 3. ROLE_FLAG_CAPTION track matching preferred language stem
                    // 4. ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND track matching preferred language stem
                    // This means if a caption track of preferred language is not available,
                    // then an auto-generated track of that language will be chosen automatically.
                    player.getTrackSelector().setParameters(player.getTrackSelector()
                            .buildUponParameters()
                            .setPreferredTextLanguages(captionLanguage,
                                    PlayerHelper.captionLanguageStemOf(captionLanguage))
                            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                            .setRendererDisabled(textRendererIndex, false));
                    player.getPrefs().edit().putString(context.getString(
                            R.string.caption_user_set_key), captionLanguage).apply();
                }
                return true;
            });
        }
        captionPopupMenu.setOnDismissListener(this);

        // apply caption language from previous user preference
        final int textRendererIndex = player.getCaptionRendererIndex();
        if (textRendererIndex == RENDERER_UNAVAILABLE) {
            return;
        }

        // If user prefers to show no caption, then disable the renderer.
        // Otherwise, DefaultTrackSelector may automatically find an available caption
        // and display that.
        final String userPreferredLanguage =
                player.getPrefs().getString(context.getString(R.string.caption_user_set_key), null);
        if (userPreferredLanguage == null) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setRendererDisabled(textRendererIndex, true));
            return;
        }

        // Only set preferred language if it does not match the user preference,
        // otherwise there might be an infinite cycle at onTextTracksChanged.
        final List<String> selectedPreferredLanguages =
                player.getTrackSelector().getParameters().preferredTextLanguages;
        if (!selectedPreferredLanguages.contains(userPreferredLanguage)) {
            player.getTrackSelector().setParameters(player.getTrackSelector().buildUponParameters()
                    .setPreferredTextLanguages(userPreferredLanguage,
                            PlayerHelper.captionLanguageStemOf(userPreferredLanguage))
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION)
                    .setRendererDisabled(textRendererIndex, false));
        }
    }
