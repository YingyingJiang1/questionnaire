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


    private static String generateEnhancedEmailHtml(EmailContent content, EmlToPdfRequest request) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><meta charset=\"UTF-8\">\n");
        html.append("<title>").append(escapeHtml(content.getSubject())).append("</title>\n");
        html.append("<style>\n");
        appendEnhancedStyles(html);
        html.append("</style>\n");
        html.append("</head><body>\n");

        html.append("<div class=\"email-container\">\n");
        html.append("<div class=\"email-header\">\n");
        html.append("<h1>").append(escapeHtml(content.getSubject())).append("</h1>\n");
        html.append("<div class=\"email-meta\">\n");
        html.append("<div><strong>From:</strong> ")
                .append(escapeHtml(content.getFrom()))
                .append("</div>\n");
        html.append("<div><strong>To:</strong> ")
                .append(escapeHtml(content.getTo()))
                .append("</div>\n");

        if (content.getDate() != null) {
            html.append("<div><strong>Date:</strong> ")
                    .append(formatEmailDate(content.getDate()))
                    .append("</div>\n");
        }
        html.append("</div></div>\n");

        html.append("<div class=\"email-body\">\n");
        if (content.getHtmlBody() != null && !content.getHtmlBody().trim().isEmpty()) {
            html.append(processEmailHtmlBody(content.getHtmlBody(), content));
        } else if (content.getTextBody() != null && !content.getTextBody().trim().isEmpty()) {
            html.append("<div class=\"text-body\">");
            html.append(convertTextToHtml(content.getTextBody()));
            html.append("</div>");
        } else {
            html.append("<div class=\"no-content\">");
            html.append("<p><em>No content available</em></p>");
            html.append("</div>");
        }
        html.append("</div>\n");

        if (content.getAttachmentCount() > 0 || !content.getAttachments().isEmpty()) {
            html.append("<div class=\"attachment-section\">\n");
            int displayedAttachmentCount =
                    content.getAttachmentCount() > 0
                            ? content.getAttachmentCount()
                            : content.getAttachments().size();
            html.append("<h3>Attachments (").append(displayedAttachmentCount).append(")</h3>\n");

            if (!content.getAttachments().isEmpty()) {
                for (EmailAttachment attachment : content.getAttachments()) {
                    // Create attachment info with paperclip emoji before filename
                    String uniqueId = generateUniqueAttachmentId(attachment.getFilename());
                    attachment.setEmbeddedFilename(
                            attachment.getEmbeddedFilename() != null
                                    ? attachment.getEmbeddedFilename()
                                    : attachment.getFilename());

                    html.append("<div class=\"attachment-item\" id=\"")
                            .append(uniqueId)
                            .append("\">")
                            .append("<span class=\"attachment-icon\">")
                            .append(MimeConstants.ATTACHMENT_MARKER)
                            .append("</span> ")
                            .append("<span class=\"attachment-name\">")
                            .append(escapeHtml(safeMimeDecode(attachment.getFilename())))
                            .append("</span>");

                    String sizeStr = formatFileSize(attachment.getSizeBytes());
                    html.append(" <span class=\"attachment-details\">(").append(sizeStr);
                    if (attachment.getContentType() != null
                            && !attachment.getContentType().isEmpty()) {
                        html.append(", ").append(escapeHtml(attachment.getContentType()));
                    }
                    html.append(")</span></div>\n");
                }
            }

            if (request.isIncludeAttachments()) {
                html.append("<div class=\"attachment-info-note\">\n");
                html.append("<p><em>Attachments are embedded in the file.</em></p>\n");
                html.append("</div>\n");
            } else {
                html.append("<div class=\"attachment-info-note\">\n");
                html.append(
                        "<p><em>Attachment information displayed - files not included in PDF.</em></p>\n");
                html.append("</div>\n");
            }

            html.append("</div>\n");
        }

        html.append("</div>\n");
        html.append("</body></html>");

        return html.toString();
    }


