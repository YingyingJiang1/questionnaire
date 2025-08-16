
    public SSLContext createSslContext() throws IOException, GeneralSecurityException {
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        if (sslVerifyMode == SslVerifyMode.FULL) {
            this.sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        } else if (sslVerifyMode == SslVerifyMode.CA) {
            this.sslParameters.setEndpointIdentificationAlgorithm("");
        } else if (sslVerifyMode == SslVerifyMode.INSECURE) {
            trustManagers = new TrustManager[] { INSECURE_TRUST_MANAGER };
        }

        if (keystoreResource != null) {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            InputStream keystoreStream = null;
            try {
                keystoreStream = keystoreResource.get();
                keyStore.load(keystoreStream, keystorePassword);
            } finally {
                if (keystoreStream != null) {
                    try {
                        keystoreStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm);
            keyManagerFactory.init(keyStore, keystorePassword);
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        if (trustManagers == null && truststoreResource != null) {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            InputStream truststoreStream = null;
            try {
                truststoreStream = truststoreResource.get();
                trustStore.load(truststoreStream, truststorePassword);
            } finally {
                if (truststoreStream != null) {
                    try {
                        truststoreStream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(trustManagerAlgorithm);
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }

        SSLContext sslContext = SSLContext.getInstance(sslProtocol);
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

