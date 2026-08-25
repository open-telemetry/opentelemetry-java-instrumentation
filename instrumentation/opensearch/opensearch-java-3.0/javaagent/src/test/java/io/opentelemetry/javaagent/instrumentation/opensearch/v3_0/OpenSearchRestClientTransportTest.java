/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.Node;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

// RestClientTransport is deprecated but still correct for OpenSearch Java 3.0.
@SuppressWarnings("deprecation")
class OpenSearchRestClientTransportTest extends AbstractOpenSearchTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() throws Exception {
    return new OpenSearchClient(buildTransport(configuredHost()));
  }

  @Override
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception {
    return new OpenSearchAsyncClient(buildTransport(configuredHost()));
  }

  @Test
  void configuredNodeListIsTheWholeTarget() throws Exception {
    try (OpenSearchTransport transport = buildTransport(configuredHost(), hostThatIsDown())) {
      OpenSearchClient nodeListClient = new OpenSearchClient(transport);

      HealthResponse healthResponse = nodeListClient.cluster().health();
      assertThat(healthResponse).isNotNull();

      assertNodeListTarget();
    }
  }

  @Test
  void targetDoesNotFollowNodeChangesBeforeTransportConstruction() throws Exception {
    RestClient restClient = buildRestClient(configuredHost());
    restClient.setNodes(asList(new Node(configuredHost()), new Node(hostThatIsDown())));
    try (OpenSearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper())) {
      OpenSearchClient client = new OpenSearchClient(transport);

      HealthResponse healthResponse = client.cluster().health();
      assertThat(healthResponse).isNotNull();

      getTesting()
          .waitAndAssertTraces(
              trace -> assertThat(trace.getSpan(0)).hasAttributesSatisfying(withServer()));
    }
  }

  private HttpHost configuredHost() {
    return new HttpHost("https", httpHost.getHost(), httpHost.getPort());
  }

  private HttpHost hostThatIsDown() {
    // nothing listens on this port, so the request is served by the running server after a retry
    return new HttpHost("https", httpHost.getHost(), httpHost.getPort() + 1);
  }

  private OpenSearchTransport buildTransport(HttpHost... hosts) throws Exception {
    return new RestClientTransport(buildRestClient(hosts), new JacksonJsonpMapper());
  }

  private RestClient buildRestClient(HttpHost... hosts) throws Exception {
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

    return RestClient.builder(hosts)
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider))
        .build();
  }
}
