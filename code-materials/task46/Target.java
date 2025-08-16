    private void setupMainFrame() {
        frame = new JFrame("Stirling-PDF");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setOpacity(0.0f);

        JPanel contentPane = new JPanel(new BorderLayout());
        contentPane.setDoubleBuffered(true);
        contentPane.add(browser.getUIComponent(), BorderLayout.CENTER);
        frame.setContentPane(contentPane);

        frame.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                        cleanup();
                        System.exit(0);
                    }
                });

        frame.setSize(UIScaling.scaleWidth(1280), UIScaling.scaleHeight(800));
        frame.setLocationRelativeTo(null);

        loadIcon();
    }


    private boolean verifyJWTLicense(String licenseKey, LicenseContext context) {
        try {
            log.info("Verifying ED25519_SIGN format license key");

            // Remove the "key/" prefix
            String licenseData = licenseKey.substring(JWT_PREFIX.length());

            // Split into payload and signature
            String[] parts = licenseData.split("\\.", 2);
            if (parts.length != 2) {
                log.error(
                        "Invalid ED25519_SIGN license format. Expected format:"
                                + " key/payload.signature");
                return false;
            }

            String encodedPayload = parts[0];
            String encodedSignature = parts[1];

            // Verify signature
            boolean isSignatureValid = verifyJWTSignature(encodedPayload, encodedSignature);
            if (!isSignatureValid) {
                log.error("ED25519_SIGN license signature is invalid");
                return false;
            }

            log.info("ED25519_SIGN license signature is valid");

            // Decode and process payload - first convert from URL-safe base64 if needed
            String base64Payload = encodedPayload.replace('-', '+').replace('_', '/');
            byte[] payloadBytes = Base64.getDecoder().decode(base64Payload);
            String payload = new String(payloadBytes);

            // Process the license payload
            boolean isValid = processJWTLicensePayload(payload, context);

            return isValid;
        } catch (Exception e) {
            log.error("Error verifying ED25519_SIGN license: {}", e.getMessage());
            return false;
        }
    }


    @SuppressWarnings("deprecation")
    public void migrateEnterpriseSettingsToPremium(ApplicationProperties applicationProperties) {
        EnterpriseEdition enterpriseEdition = applicationProperties.getEnterpriseEdition();
        Premium premium = applicationProperties.getPremium();

        // Only proceed if both objects exist
        if (enterpriseEdition == null || premium == null) {
            return;
        }

        // Copy the license key if it's set in enterprise but not in premium
        if (premium.getKey() == null
                || "00000000-0000-0000-0000-000000000000".equals(premium.getKey())) {
            if (enterpriseEdition.getKey() != null
                    && !"00000000-0000-0000-0000-000000000000".equals(enterpriseEdition.getKey())) {
                premium.setKey(enterpriseEdition.getKey());
            }
        }

        // Copy enabled state if enterprise is enabled but premium is not
        if (!premium.isEnabled() && enterpriseEdition.isEnabled()) {
            premium.setEnabled(true);
        }

        // Copy SSO auto login setting
        if (!premium.getProFeatures().isSsoAutoLogin() && enterpriseEdition.isSsoAutoLogin()) {
            premium.getProFeatures().setSsoAutoLogin(true);
        }

        // Copy CustomMetadata settings
        Premium.ProFeatures.CustomMetadata premiumMetadata =
                premium.getProFeatures().getCustomMetadata();
        EnterpriseEdition.CustomMetadata enterpriseMetadata = enterpriseEdition.getCustomMetadata();

        if (enterpriseMetadata != null && premiumMetadata != null) {
            // Copy autoUpdateMetadata setting
            if (!premiumMetadata.isAutoUpdateMetadata()
                    && enterpriseMetadata.isAutoUpdateMetadata()) {
                premiumMetadata.setAutoUpdateMetadata(true);
            }

            // Copy author if not set in premium but set in enterprise
            if ((premiumMetadata.getAuthor() == null
                            || premiumMetadata.getAuthor().trim().isEmpty()
                            || "username".equals(premiumMetadata.getAuthor()))
                    && enterpriseMetadata.getAuthor() != null
                    && !enterpriseMetadata.getAuthor().trim().isEmpty()) {
                premiumMetadata.setAuthor(enterpriseMetadata.getAuthor());
            }

            // Copy creator if not set in premium but set in enterprise and different from default
            if ((premiumMetadata.getCreator() == null
                            || "Stirling-PDF".equals(premiumMetadata.getCreator()))
                    && enterpriseMetadata.getCreator() != null
                    && !"Stirling-PDF".equals(enterpriseMetadata.getCreator())) {
                premiumMetadata.setCreator(enterpriseMetadata.getCreator());
            }

            // Copy producer if not set in premium but set in enterprise and different from default
            if ((premiumMetadata.getProducer() == null
                            || "Stirling-PDF".equals(premiumMetadata.getProducer()))
                    && enterpriseMetadata.getProducer() != null
                    && !"Stirling-PDF".equals(enterpriseMetadata.getProducer())) {
                premiumMetadata.setProducer(enterpriseMetadata.getProducer());
            }
        }
    }


    @PostMapping(value = "/handleData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> handleData(@ModelAttribute HandleDataRequest request)
            throws JsonMappingException, JsonProcessingException {
        MultipartFile[] files = request.getFileInput();
        String jsonString = request.getJson();
        if (files == null) {
            return null;
        }
        PipelineConfig config = objectMapper.readValue(jsonString, PipelineConfig.class);
        log.info("Received POST request to /handleData with {} files", files.length);

        List<String> operationNames =
                config.getOperations().stream().map(PipelineOperation::getOperation).toList();

        Map<String, Object> properties = new HashMap<>();
        properties.put("operations", operationNames);
        properties.put("fileCount", files.length);

        postHogService.captureEvent("pipeline_api_event", properties);

        try {
            List<Resource> inputFiles = processor.generateInputFiles(files);
            if (inputFiles == null || inputFiles.size() == 0) {
                return null;
            }
            PipelineResult result = processor.runPipelineAgainstFiles(inputFiles, config);
            List<Resource> outputFiles = result.getOutputFiles();
            if (outputFiles != null && outputFiles.size() == 1) {
                // If there is only one file, return it directly
                Resource singleFile = outputFiles.get(0);
                InputStream is = singleFile.getInputStream();
                byte[] bytes = new byte[(int) singleFile.contentLength()];
                is.read(bytes);
                is.close();
                log.info("Returning single file response...");
                return WebResponseUtils.bytesToWebResponse(
                        bytes, singleFile.getFilename(), MediaType.APPLICATION_OCTET_STREAM);
            } else if (outputFiles == null) {
                return null;
            }
            // Create a ByteArrayOutputStream to hold the zip
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zipOut = new ZipOutputStream(baos);
            // A map to keep track of filenames and their counts
            Map<String, Integer> filenameCount = new HashMap<>();
            // Loop through each file and add it to the zip
            for (Resource file : outputFiles) {
                String originalFilename = file.getFilename();
                String filename = originalFilename;
                // Check if the filename already exists, and modify it if necessary
                if (filenameCount.containsKey(originalFilename)) {
                    int count = filenameCount.get(originalFilename);
                    String baseName = originalFilename.replaceAll("\\.[^.]*$", "");
                    String extension = originalFilename.replaceAll("^.*\\.", "");
                    filename = baseName + "(" + count + ")." + extension;
                    filenameCount.put(originalFilename, count + 1);
                } else {
                    filenameCount.put(originalFilename, 1);
                }
                ZipEntry zipEntry = new ZipEntry(filename);
                zipOut.putNextEntry(zipEntry);
                // Read the file into a byte array
                InputStream is = file.getInputStream();
                byte[] bytes = new byte[(int) file.contentLength()];
                is.read(bytes);
                // Write the bytes of the file to the zip
                zipOut.write(bytes, 0, bytes.length);
                zipOut.closeEntry();
                is.close();
            }
            zipOut.close();
            log.info("Returning zipped file response...");
            return WebResponseUtils.baosToWebResponse(
                    baos, "output.zip", MediaType.APPLICATION_OCTET_STREAM);
        } catch (Exception e) {
            log.error("Error handling data: ", e);
            return null;
        }
    }


