    static BufferedImage convertColorType(BufferedImage sourceImage, String colorType) {
        BufferedImage convertedImage;
        switch (colorType) {
            case "greyscale":
                convertedImage =
                        new BufferedImage(
                                sourceImage.getWidth(),
                                sourceImage.getHeight(),
                                BufferedImage.TYPE_BYTE_GRAY);
                convertedImage.getGraphics().drawImage(sourceImage, 0, 0, null);
                break;
            case "blackwhite":
                convertedImage =
                        new BufferedImage(
                                sourceImage.getWidth(),
                                sourceImage.getHeight(),
                                BufferedImage.TYPE_BYTE_BINARY);
                convertedImage.getGraphics().drawImage(sourceImage, 0, 0, null);
                break;
            default: // full color
                convertedImage = sourceImage;
                break;
        }
        return convertedImage;
    }


    private void cleanupTempFiles() {
        try {
            // Clean up all registered files
            Set<Path> files = registry.getAllRegisteredFiles();
            int deletedCount = 0;

            for (Path file : files) {
                try {
                    if (Files.exists(file)) {
                        Files.deleteIfExists(file);
                        deletedCount++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp file during shutdown: {}", file, e);
                }
            }

            // Clean up all registered directories
            Set<Path> directories = registry.getTempDirectories();
            for (Path dir : directories) {
                try {
                    if (Files.exists(dir)) {
                        GeneralUtils.deleteDirectory(dir);
                        deletedCount++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete temp directory during shutdown: {}", dir, e);
                }
            }

            log.info(
                    "Shutdown cleanup complete. Deleted {} temporary files/directories",
                    deletedCount);

            // Clear the registry
            registry.clear();
        } catch (Exception e) {
            log.error("Error during shutdown cleanup", e);
        }
    }


    @PostMapping(consumes = "multipart/form-data", value = "/pdf-to-single-page")
    @Operation(
            summary = "Convert a multi-page PDF into a single long page PDF",
            description =
                    "This endpoint converts a multi-page PDF document into a single paged PDF"
                            + " document. The width of the single page will be same as the input's"
                            + " width, but the height will be the sum of all the pages' heights."
                            + " Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> pdfToSinglePage(@ModelAttribute PDFFile request)
            throws IOException {

        // Load the source document
        PDDocument sourceDocument = pdfDocumentFactory.load(request);

        // Calculate total height and max width
        float totalHeight = 0;
        float maxWidth = 0;
        for (PDPage page : sourceDocument.getPages()) {
            PDRectangle pageSize = page.getMediaBox();
            totalHeight += pageSize.getHeight();
            maxWidth = Math.max(maxWidth, pageSize.getWidth());
        }

        // Create new document and page with calculated dimensions
        PDDocument newDocument =
                pdfDocumentFactory.createNewDocumentBasedOnOldDocument(sourceDocument);
        PDPage newPage = new PDPage(new PDRectangle(maxWidth, totalHeight));
        newDocument.addPage(newPage);

        // Initialize the content stream of the new page
        PDPageContentStream contentStream = new PDPageContentStream(newDocument, newPage);
        contentStream.close();

        LayerUtility layerUtility = new LayerUtility(newDocument);
        float yOffset = totalHeight;

        // For each page, copy its content to the new page at the correct offset
        int pageIndex = 0;
        for (PDPage page : sourceDocument.getPages()) {
            PDFormXObject form = layerUtility.importPageAsForm(sourceDocument, pageIndex);
            AffineTransform af =
                    AffineTransform.getTranslateInstance(
                            0, yOffset - page.getMediaBox().getHeight());
            layerUtility.wrapInSaveRestore(newPage);
            String defaultLayerName = "Layer" + pageIndex;
            layerUtility.appendFormAsLayer(newPage, form, af, defaultLayerName);
            yOffset -= page.getMediaBox().getHeight();
            pageIndex++;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        newDocument.save(baos);
        newDocument.close();
        sourceDocument.close();

        byte[] result = baos.toByteArray();
        return WebResponseUtils.bytesToWebResponse(
                result,
                request.getFileInput().getOriginalFilename().replaceFirst("[.][^.]+$", "")
                        + "_singlePage.pdf");
    }


    @PostMapping(value = "/split-pdf-by-chapters", consumes = "multipart/form-data")
    @Operation(
            summary = "Split PDFs by Chapters",
            description = "Splits a PDF into chapters and returns a ZIP file.")
    public ResponseEntity<byte[]> splitPdf(@ModelAttribute SplitPdfByChaptersRequest request)
            throws Exception {
        MultipartFile file = request.getFileInput();
        PDDocument sourceDocument = null;
        Path zipFile = null;

        try {
            boolean includeMetadata = Boolean.TRUE.equals(request.getIncludeMetadata());
            Integer bookmarkLevel =
                    request.getBookmarkLevel(); // levels start from 0 (top most bookmarks)
            if (bookmarkLevel < 0) {
                throw ExceptionUtils.createIllegalArgumentException(
                        "error.invalidArgument", "Invalid argument: {0}", "bookmark level");
            }
            sourceDocument = pdfDocumentFactory.load(file);

            PDDocumentOutline outline = sourceDocument.getDocumentCatalog().getDocumentOutline();

            if (outline == null) {
                log.warn("No outline found for {}", file.getOriginalFilename());
                throw ExceptionUtils.createIllegalArgumentException(
                        "error.pdfBookmarksNotFound", "No PDF bookmarks/outline found in document");
            }
            List<Bookmark> bookmarks = new ArrayList<>();
            try {
                bookmarks =
                        extractOutlineItems(
                                sourceDocument,
                                outline.getFirstChild(),
                                bookmarks,
                                outline.getFirstChild().getNextSibling(),
                                0,
                                bookmarkLevel);
                // to handle last page edge case
                bookmarks.get(bookmarks.size() - 1).setEndPage(sourceDocument.getNumberOfPages());
                Bookmark lastBookmark = bookmarks.get(bookmarks.size() - 1);

            } catch (Exception e) {
                ExceptionUtils.logException("outline extraction", e);
                return ResponseEntity.internalServerError()
                        .body("Unable to extract outline items".getBytes());
            }

            boolean allowDuplicates = Boolean.TRUE.equals(request.getAllowDuplicates());
            if (!allowDuplicates) {
                /*
                duplicates are generated when multiple bookmarks correspond to the same page,
                if the user doesn't want duplicates mergeBookmarksThatCorrespondToSamePage() method will merge the titles of all
                the bookmarks that correspond to the same page, and treat them as a single bookmark
                */
                bookmarks = mergeBookmarksThatCorrespondToSamePage(bookmarks);
            }
            for (Bookmark bookmark : bookmarks) {
                log.info(
                        "{}::::{} to {}",
                        bookmark.getTitle(),
                        bookmark.getStartPage(),
                        bookmark.getEndPage());
            }
            List<ByteArrayOutputStream> splitDocumentsBoas =
                    getSplitDocumentsBoas(sourceDocument, bookmarks, includeMetadata);

            zipFile = createZipFile(bookmarks, splitDocumentsBoas);

            byte[] data = Files.readAllBytes(zipFile);
            Files.deleteIfExists(zipFile);

            String filename =
                    Filenames.toSimpleFileName(file.getOriginalFilename())
                            .replaceFirst("[.][^.]+$", "");
            sourceDocument.close();
            return WebResponseUtils.bytesToWebResponse(
                    data, filename + ".zip", MediaType.APPLICATION_OCTET_STREAM);
        } finally {
            try {
                if (sourceDocument != null) {
                    sourceDocument.close();
                }
                if (zipFile != null) {
                    Files.deleteIfExists(zipFile);
                }
            } catch (Exception e) {
                log.error("Error while cleaning up resources", e);
            }
        }
    }


