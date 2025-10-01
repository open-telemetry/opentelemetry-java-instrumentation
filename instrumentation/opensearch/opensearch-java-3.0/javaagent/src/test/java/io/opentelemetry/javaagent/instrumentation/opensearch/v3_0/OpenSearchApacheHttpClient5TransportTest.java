/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

class OpenSearchApacheHttpClient5TransportTest extends AbstractOpenSearchJavaTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() throws Exception {
    HttpHost host = new HttpHost("https", httpHost.getHost(), httpHost.getPort());

    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    TlsStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setSslContext(sslContext)
            .build();
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            opensearch.getUsername(), opensearch.getPassword().toCharArray()));

    OpenSearchTransport apacheHttpClient5Transport =
        ApacheHttpClient5TransportBuilder.builder(host)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setConnectionManager(connectionManager)
                        .setDefaultCredentialsProvider(credentialsProvider))
            .build();
    return new OpenSearchClient(apacheHttpClient5Transport);
  }

  @Override
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception {
    HttpHost host = new HttpHost("https", httpHost.getHost(), httpHost.getPort());

    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    TlsStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setSslContext(sslContext)
            .build();
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            opensearch.getUsername(), opensearch.getPassword().toCharArray()));

    OpenSearchTransport apacheHttpClient5Transport =
        ApacheHttpClient5TransportBuilder.builder(host)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setConnectionManager(connectionManager)
                        .setDefaultCredentialsProvider(credentialsProvider))
            .build();
    return new OpenSearchAsyncClient(apacheHttpClient5Transport);
  }
}
