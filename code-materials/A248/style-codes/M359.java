    private static EmailContent extractEmailContentAdvanced(
            Object message, EmlToPdfRequest request) {
        EmailContent content = new EmailContent();

        try {
            Class<?> messageClass = message.getClass();

            // Extract headers via reflection
            Method getSubject = messageClass.getMethod("getSubject");
            String subject = (String) getSubject.invoke(message);
            content.setSubject(subject != null ? safeMimeDecode(subject) : "No Subject");

            Method getFrom = messageClass.getMethod("getFrom");
            Object[] fromAddresses = (Object[]) getFrom.invoke(message);
            content.setFrom(
                    fromAddresses != null && fromAddresses.length > 0
                            ? safeMimeDecode(fromAddresses[0].toString())
                            : "");

            Method getAllRecipients = messageClass.getMethod("getAllRecipients");
            Object[] recipients = (Object[]) getAllRecipients.invoke(message);
            content.setTo(
                    recipients != null && recipients.length > 0
                            ? safeMimeDecode(recipients[0].toString())
                            : "");

            Method getSentDate = messageClass.getMethod("getSentDate");
            content.setDate((Date) getSentDate.invoke(message));

            // Extract content
            Method getContent = messageClass.getMethod("getContent");
            Object messageContent = getContent.invoke(message);

            if (messageContent instanceof String stringContent) {
                Method getContentType = messageClass.getMethod("getContentType");
                String contentType = (String) getContentType.invoke(message);
                if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                    content.setHtmlBody(stringContent);
                } else {
                    content.setTextBody(stringContent);
                }
            } else {
                // Handle multipart content
                try {
                    Class<?> multipartClass = Class.forName("jakarta.mail.Multipart");
                    if (multipartClass.isInstance(messageContent)) {
                        processMultipartAdvanced(messageContent, content, request);
                    }
                } catch (Exception e) {
                    log.warn("Error processing content: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            content.setSubject("Email Conversion");
            content.setFrom("Unknown");
            content.setTo("Unknown");
            content.setTextBody("Email content could not be parsed with advanced processing");
        }

        return content;
    }
