    @Override
    public void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();

            logger.info("receive TextWebSocketFrame: {}", text);

            QueryStringDecoder queryDecoder = new QueryStringDecoder(text);
            Map<String, List<String>> parameters = queryDecoder.parameters();
            List<String> methodList = parameters.get(URIConstans.METHOD);
            String method = null;
            if (methodList != null && !methodList.isEmpty()) {
                method = methodList.get(0);
            }

            if (MethodConstants.AGENT_REGISTER.equals(method)) {
                List<String> idList = parameters.get(URIConstans.ID);
                if (idList != null && !idList.isEmpty()) {
                    this.tunnelClient.setId(idList.get(0));
                }
                tunnelClient.setConnected(true);
                registerPromise.setSuccess();
            }

            if (MethodConstants.START_TUNNEL.equals(method)) {
                QueryStringEncoder queryEncoder = new QueryStringEncoder(this.tunnelClient.getTunnelServerUrl());
                queryEncoder.addParam(URIConstans.METHOD, MethodConstants.OPEN_TUNNEL);
                queryEncoder.addParam(URIConstans.CLIENT_CONNECTION_ID, parameters.get(URIConstans.CLIENT_CONNECTION_ID).get(0));
                queryEncoder.addParam(URIConstans.ID, parameters.get(URIConstans.ID).get(0));

                final URI forwardUri = queryEncoder.toUri();

                logger.info("start ForwardClient, uri: {}", forwardUri);
                try {
                    ForwardClient forwardClient = new ForwardClient(forwardUri);
                    forwardClient.start();
                } catch (Throwable e) {
                    logger.error("start ForwardClient error, forwardUri: {}", forwardUri, e);
                }
            }

            if (MethodConstants.HTTP_PROXY.equals(method)) {
                /**
                 * <pre>
                 * 1. 从proxy请求里读取到目标的 targetUrl，和 requestId
                 * 2. 然后通过 ProxyClient直接请求得到结果
                 * 3. 把response结果转为 byte[]，再转为base64，再统一组合的一个url，再用 TextWebSocketFrame 发回去
                 * </pre>
                 * 
                 */
                ProxyClient proxyClient = new ProxyClient();
                List<String> targetUrls = parameters.get(URIConstans.TARGET_URL);

                List<String> requestIDs = parameters.get(URIConstans.PROXY_REQUEST_ID);
                String id = null;
                if (requestIDs != null && !requestIDs.isEmpty()) {
                    id = requestIDs.get(0);
                }
                if (id == null) {
                    logger.error("error, http proxy need {}", URIConstans.PROXY_REQUEST_ID);
                    return;
                }

                if (targetUrls != null && !targetUrls.isEmpty()) {
                    String targetUrl = targetUrls.get(0);
                    SimpleHttpResponse simpleHttpResponse = proxyClient.query(targetUrl);

                    ByteBuf byteBuf = null;
                    try{
                        byteBuf = Base64
                                .encode(Unpooled.wrappedBuffer(SimpleHttpResponse.toBytes(simpleHttpResponse)));
                        String requestData = byteBuf.toString(CharsetUtil.UTF_8);

                        QueryStringEncoder queryEncoder = new QueryStringEncoder("");
                        queryEncoder.addParam(URIConstans.METHOD, MethodConstants.HTTP_PROXY);
                        queryEncoder.addParam(URIConstans.PROXY_REQUEST_ID, id);
                        queryEncoder.addParam(URIConstans.PROXY_RESPONSE_DATA, requestData);

                        String url = queryEncoder.toString();
                        ctx.writeAndFlush(new TextWebSocketFrame(url));
                    }finally {
                        if (byteBuf != null) {
                            byteBuf.release();
                        }
                    }
                }
            }

        }
    }
