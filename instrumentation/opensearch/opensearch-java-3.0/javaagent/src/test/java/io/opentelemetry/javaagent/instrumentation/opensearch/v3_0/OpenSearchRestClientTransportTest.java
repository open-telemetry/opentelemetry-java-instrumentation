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
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

@SuppressWarnings(
    "deprecation") // RestClientTransport is deprecated but still the correct way for OpenSearch
// Java 3.0
class OpenSearchRestClientTransportTest extends AbstractOpenSearchJavaTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() throws Exception {
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

    HttpHost httpHost = HttpHost.create(opensearch.getHttpHostAddress());
    RestClient restClient =
        RestClient.builder(httpHost)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setConnectionManager(connectionManager)
                        .setDefaultCredentialsProvider(credentialsProvider))
            .build();

    OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    return new OpenSearchClient(transport);
  }

  @Override
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception {
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

    HttpHost httpHost = HttpHost.create(opensearch.getHttpHostAddress());
    RestClient restClient =
        RestClient.builder(httpHost)
            .setHttpClientConfigCallback(
                httpClientBuilder ->
                    httpClientBuilder
                        .setConnectionManager(connectionManager)
                        .setDefaultCredentialsProvider(credentialsProvider))
            .build();

    OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    return new OpenSearchAsyncClient(transport);
  }
}
