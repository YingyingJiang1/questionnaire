
public static String generateMachineFingerprint() {
    try {
        StringBuilder sb = new StringBuilder();
        InetAddress ip = InetAddress.getLocalHost();
        NetworkInterface network = NetworkInterface.getByInetAddress(ip);
        if (network == null) {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface net = networks.nextElement();
                byte[] mac = net.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    break;
                }
            }
        } else {
            byte[] mac = network.getHardwareAddress();
            if (mac != null) for (byte b : mac) sb.append(String.format("%02X", b));
        }

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder fingerprint = new StringBuilder();
        for (byte b : hash) fingerprint.append(String.format("%02x", b));
        return fingerprint.toString();
    } catch (Exception e) {
        return "GenericID";
    }
}
