/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v3_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.AbstractOpenSearchRestTest;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
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
import org.opensearch.client.Node;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;

class OpenSearchRest3Test extends AbstractOpenSearchRestTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected RestClient buildRestClient(String... hostAddresses) throws Exception {
    BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(null, -1),
        new UsernamePasswordCredentials(
            opensearch.getUsername(), opensearch.getPassword().toCharArray()));

    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    TlsStrategy tlsStrategy =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            // Required for non-localhost Docker runtimes, the SSL cert in the
            // OpenSearch image is registered to "localhost"
            .setSslContext(sslContext)
            .build();
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

    return RestClient.builder(httpHosts(hostAddresses))
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider))
        .build();
  }

  @Override
  protected void resetNodes(RestClient client, String... hostAddresses) throws URISyntaxException {
    List<Node> nodes = new ArrayList<>();
    for (HttpHost httpHost : httpHosts(hostAddresses)) {
      nodes.add(new Node(httpHost));
    }
    client.setNodes(nodes);
  }

  private static HttpHost[] httpHosts(String... hostAddresses) throws URISyntaxException {
    HttpHost[] httpHosts = new HttpHost[hostAddresses.length];
    for (int i = 0; i < hostAddresses.length; i++) {
      httpHosts[i] = HttpHost.create(hostAddresses[i]);
    }
    return httpHosts;
  }

  @Override
  protected int getResponseStatus(Response response) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  protected String getResponseAddress(Response response) {
    return response.getHost().getAddress() != null
        ? response.getHost().getAddress().getHostAddress()
        : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.opensearch-rest-3.0";
  }
}
