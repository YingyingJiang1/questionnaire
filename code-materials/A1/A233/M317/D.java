
public static void addImageToDocument(
    PDDocument doc, PDImageXObject image, String fitOption, boolean autoRotate) throws IOException {
  boolean imageIsLandscape = image.getWidth() > image.getHeight();
  PDRectangle pageSize = PDRectangle.A4;

  if (autoRotate && imageIsLandscape) {
    pageSize = new PDRectangle(pageSize.getHeight(), pageSize.getWidth());
  }

  if ("fitDocumentToImage".equals(fitOption)) {
    pageSize = new PDRectangle(image.getWidth(), image.getHeight());
  }

  PDPage page = new PDPage(pageSize);
  doc.addPage(page);
  
  float pageWidth = page.getMediaBox().getWidth();
  float pageHeight = page.getMediaBox().getHeight();

  try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, AppendMode.APPEND, true, true)) {
    if ("fillPage".equals(fitOption) || "fitDocumentToImage".equals(fitOption)) {
      contentStream.drawImage(image, 0, 0, pageWidth, pageHeight);
    } else if ("maintainAspectRatio".equals(fitOption)) {
      float imageAspectRatio = (float) image.getWidth() / image.getHeight();
      float pageAspectRatio = pageWidth / pageHeight;
      float scaleFactor = imageAspectRatio > pageAspectRatio 
          ? pageWidth / image.getWidth() 
          : pageHeight / image.getHeight();

      float xPos = (pageWidth - (image.getWidth() * scaleFactor)) / 2;
      float yPos = (pageHeight - (image.getHeight() * scaleFactor)) / 2;
      contentStream.drawImage(image, xPos, yPos, image.getWidth() * scaleFactor, image.getHeight() * scaleFactor);
    }
  }
}
