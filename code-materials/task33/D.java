
private static void addAttachmentAnnotationsToDocument(
        PDDocument document, List<EmailAttachment> attachments) throws IOException {
    if (document.getNumberOfPages() == 0 || attachments == null || attachments.isEmpty()) {
        return;
    }

    // 1. Find the screen position of all attachment markers
    AttachmentMarkerPositionFinder finder = new AttachmentMarkerPositionFinder();
    finder.setSortByPosition(true); // Process pages in order
    finder.getText(document);
    List<MarkerPosition> markerPositions = finder.getPositions();

    // 2. Warn if the number of markers and attachments don't match
    if (markerPositions.size() != attachments.size()) {
        log.warn(
                "Found {} attachment markers, but there are {} attachments. Annotation count may be incorrect.",
                markerPositions.size(),
                attachments.size());
    }

    // 3. Create an invisible annotation over each found marker
    int annotationsToAdd = Math.min(markerPositions.size(), attachments.size());
    for (int i = 0; i < annotationsToAdd; i++) {
        MarkerPosition position = markerPositions.get(i);
        EmailAttachment attachment = attachments.get(i);

        if (attachment.getEmbeddedFilename() != null) {
            PDPage page = document.getPage(position.getPageIndex());
            addAttachmentAnnotationToPage(
                    document, page, attachment, position.getX(), position.getY());
        }
    }
}
