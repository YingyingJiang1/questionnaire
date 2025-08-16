
@StyleRes
public static int getSettingsThemeStyle(Context context) {
    Resources res = context.getResources();
    String lightTheme = res.getString(R.string.light_theme_key);
    String blackTheme = res.getString(R.string.black_theme_key);
    String automaticDeviceTheme = res.getString(R.string.auto_device_theme_key);

    String selectedTheme = getSelectedThemeKey(context);

    if (selectedTheme.equals(lightTheme)) {
        return R.style.LightSettingsTheme;
    } else if (selectedTheme.equals(blackTheme)) {
        return R.style.BlackSettingsTheme;
    } else if (selectedTheme.equals(automaticDeviceTheme)) {
        if (isDeviceDarkThemeEnabled(context)) {
            // use the dark theme variant preferred by the user
            String selectedNightTheme = getSelectedNightThemeKey(context);
            if (selectedNightTheme.equals(blackTheme)) {
                return R.style.BlackSettingsTheme;
            } else {
                return R.style.DarkSettingsTheme;
            }
        } else {
            // there is only one day theme
            return R.style.LightSettingsTheme;
        }
    } else {
        // default to dark theme
        return R.style.DarkSettingsTheme;
    }
}
