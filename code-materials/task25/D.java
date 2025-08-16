
private static String formatFileSize(long size) {
    String hrSize;
    double b = size;
    double k = size / 1024.0;
    double m = k / 1024.0;
    double g = m / 1024.0;
    double t = g / 1024.0;

    DecimalFormat dec = new DecimalFormat("0.00");

    if (t > 1) {
        hrSize = dec.format(t).concat(" TB");
    } else if (g > 1) {
        hrSize = dec.format(g).concat(" GB");
    } else if (m > 1) {
        hrSize = dec.format(m).concat(" MB");
    } else if (k > 1) {
        hrSize = dec.format(k).concat(" KB");
    } else {
        hrSize = dec.format(b).concat(" Bytes");
    }

    return hrSize;
}
