    private synchronized void loadApiDocumentation() {
        String apiDocsJson = "";
        try {
            HttpHeaders headers = new HttpHeaders();
            String apiKey = getApiKeyForUser();
            if (!apiKey.isEmpty()) {
                headers.set("X-API-KEY", apiKey);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response =
                    restTemplate.exchange(getApiDocsUrl(), HttpMethod.GET, entity, String.class);
            apiDocsJson = response.getBody();
            ObjectMapper mapper = new ObjectMapper();
            apiDocsJsonRootNode = mapper.readTree(apiDocsJson);
            JsonNode paths = apiDocsJsonRootNode.path("paths");
            paths.propertyStream()
                    .forEach(
                            entry -> {
                                String path = entry.getKey();
                                JsonNode pathNode = entry.getValue();
                                if (pathNode.has("post")) {
                                    JsonNode postNode = pathNode.get("post");
                                    ApiEndpoint endpoint = new ApiEndpoint(path, postNode);
                                    apiDocumentation.put(path, endpoint);
                                }
                            });
        } catch (Exception e) {
            // Handle exceptions
            log.error("Error grabbing swagger doc, body result {}", apiDocsJson);
        }
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimit) {
            // If rateLimit is not enabled, just pass all requests without rate limiting
            filterChain.doFilter(request, response);
            return;
        }
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            // If the request is not a POST, just pass it through without rate limiting
            filterChain.doFilter(request, response);
            return;
        }
        String identifier = null;
        // Check for API key in the request headers
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            identifier = // Prefix to distinguish between API keys and usernames
                    "API_KEY_" + apiKey;
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                identifier = userDetails.getUsername();
            }
        }
        // If neither API key nor an authenticated user is present, use IP address
        if (identifier == null) {
            identifier = request.getRemoteAddr();
        }
        Role userRole =
                getRoleFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
        if (request.getHeader("X-API-KEY") != null) {
            // It's an API call
            processRequest(
                    userRole.getApiCallsPerDay(),
                    identifier,
                    apiBuckets,
                    request,
                    response,
                    filterChain);
        } else {
            // It's a Web UI call
            processRequest(
                    userRole.getWebCallsPerDay(),
                    identifier,
                    webBuckets,
                    request,
                    response,
                    filterChain);
        }
    }


    @Scheduled(fixedRate = 7200000) // Run every 2 hours
    public void aggregateAndSendMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        final boolean validateGetEndpoints = endpointInspector.getValidGetEndpoints().size() != 0;
        Search.in(meterRegistry)
                .name("http.requests")
                .counters()
                .forEach(
                        counter -> {
                            String method = counter.getId().getTag("method");
                            String uri = counter.getId().getTag("uri");
                            // Skip if either method or uri is null
                            if (method == null || uri == null) {
                                return;
                            }

                            // Skip URIs that are 2 characters or shorter
                            if (uri.length() <= 2) {
                                return;
                            }

                            // Skip non-GET and non-POST requests
                            if (!"GET".equals(method) && !"POST".equals(method)) {
                                return;
                            }

                            // For POST requests, only include if they start with /api/v1
                            if ("POST".equals(method) && !uri.contains("api/v1")) {
                                return;
                            }

                            if (uri.contains(".txt")) {
                                return;
                            }
                            // For GET requests, validate if we have a list of valid endpoints
                            if ("GET".equals(method)
                                    && validateGetEndpoints
                                    && !endpointInspector.isValidGetEndpoint(uri)) {
                                logger.debug("Skipping invalid GET endpoint: {}", uri);
                                return;
                            }

                            String key =
                                    String.format(
                                            "http_requests_%s_%s", method, uri.replace("/", "_"));
                            double currentCount = counter.count();
                            double lastCount = lastSentMetrics.getOrDefault(key, 0.0);
                            double difference = currentCount - lastCount;
                            if (difference > 0) {
                                logger.debug("{}, {}", key, difference);
                                metrics.put(key, difference);
                                lastSentMetrics.put(key, currentCount);
                            }
                        });
        // Send aggregated metrics to PostHog
        if (!metrics.isEmpty()) {

            postHogService.captureEvent("aggregated_metrics", metrics);
        }
    }


                    @Override
                    public void onLoadingStateChange(
                            CefBrowser browser,
                            boolean isLoading,
                            boolean canGoBack,
                            boolean canGoForward) {
                        log.debug(
                                "Loading state change - isLoading: {}, canGoBack: {}, canGoForward:"
                                        + " {}, browserInitialized: {}, Time elapsed: {}ms",
                                isLoading,
                                canGoBack,
                                canGoForward,
                                browserInitialized,
                                System.currentTimeMillis() - initStartTime);

                        if (!isLoading && !browserInitialized) {
                            log.info(
                                    "Browser finished loading, preparing to initialize UI"
                                            + " components");
                            browserInitialized = true;
                            SwingUtilities.invokeLater(
                                    () -> {
                                        try {
                                            if (loadingWindow != null) {
                                                log.info("Starting UI initialization sequence");

                                                // Close loading window first
                                                loadingWindow.setVisible(false);
                                                loadingWindow.dispose();
                                                loadingWindow = null;
                                                log.info("Loading window disposed");

                                                // Then setup the main frame
                                                frame.setVisible(false);
                                                frame.dispose();
                                                frame.setOpacity(1.0f);
                                                frame.setUndecorated(false);
                                                frame.pack();
                                                frame.setSize(
                                                        UIScaling.scaleWidth(1280),
                                                        UIScaling.scaleHeight(800));
                                                frame.setLocationRelativeTo(null);
                                                log.debug("Frame reconfigured");

                                                // Show the main frame
                                                frame.setVisible(true);
                                                frame.requestFocus();
                                                frame.toFront();
                                                log.info("Main frame displayed and focused");

                                                // Focus the browser component
                                                Timer focusTimer =
                                                        new Timer(
                                                                100,
                                                                e -> {
                                                                    try {
                                                                        browser.getUIComponent()
                                                                                .requestFocus();
                                                                        log.info(
                                                                                "Browser component"
                                                                                        + " focused");
                                                                    } catch (Exception ex) {
                                                                        log.error(
                                                                                "Error focusing"
                                                                                        + " browser",
                                                                                ex);
                                                                    }
                                                                });
                                                focusTimer.setRepeats(false);
                                                focusTimer.start();
                                            }
                                        } catch (Exception e) {
                                            log.error("Error during UI initialization", e);
                                            // Attempt cleanup on error
                                            if (loadingWindow != null) {
                                                loadingWindow.dispose();
                                                loadingWindow = null;
                                            }
                                            if (frame != null) {
                                                frame.setVisible(true);
                                                frame.requestFocus();
                                            }
                                        }
                                    });
                        }
                    }


