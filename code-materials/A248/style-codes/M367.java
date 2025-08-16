    private static String decodeMimeHeader(String encodedText) {
        if (encodedText == null || encodedText.trim().isEmpty()) {
            return encodedText;
        }

        try {
            StringBuilder result = new StringBuilder();
            Matcher matcher = MimeConstants.MIME_ENCODED_PATTERN.matcher(encodedText);
            int lastEnd = 0;

            while (matcher.find()) {
                // Add any text before the encoded part
                result.append(encodedText, lastEnd, matcher.start());

                String charset = matcher.group(1);
                String encoding = matcher.group(2).toUpperCase();
                String encodedValue = matcher.group(3);

                try {
                    String decodedValue;
                    if ("B".equals(encoding)) {
                        // Base64 decoding
                        byte[] decodedBytes = Base64.getDecoder().decode(encodedValue);
                        decodedValue = new String(decodedBytes, Charset.forName(charset));
                    } else if ("Q".equals(encoding)) {
                        // Quoted-printable decoding
                        decodedValue = decodeQuotedPrintable(encodedValue, charset);
                    } else {
                        // Unknown encoding, keep original
                        decodedValue = matcher.group(0);
                    }
                    result.append(decodedValue);
                } catch (Exception e) {
                    log.warn("Failed to decode MIME header part: {}", matcher.group(0), e);
                    // If decoding fails, keep the original encoded text
                    result.append(matcher.group(0));
                }

                lastEnd = matcher.end();
            }

            // Add any remaining text after the last encoded part
            result.append(encodedText.substring(lastEnd));

            return result.toString();
        } catch (Exception e) {
            log.warn("Error decoding MIME header: {}", encodedText, e);
            return encodedText; // Return original if decoding fails
        }
    }
