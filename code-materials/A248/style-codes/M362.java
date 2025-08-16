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
