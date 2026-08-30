/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v1_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.AbstractOpenSearchRestTest;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.ssl.SSLContextBuilder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.Node;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;

class OpenSearchRestTest extends AbstractOpenSearchRestTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected RestClient buildRestClient(String... hostAddresses) throws Exception {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(opensearch.getUsername(), opensearch.getPassword()));

    SSLContext sslContext =
        SSLContextBuilder.create().loadTrustMaterial(null, new TrustAllStrategy()).build();

    return RestClient.builder(httpHosts(hostAddresses))
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setSSLContext(sslContext)
                    // Required for non-localhost Docker runtimes, the SSL cert in the
                    // OpenSearch image is registered to "localhost"
                    .setSSLHostnameVerifier(new NoopHostnameVerifier())
                    .setDefaultCredentialsProvider(credentialsProvider))
        .build();
  }

  @Override
  protected void resetNodes(RestClient client, String... hostAddresses) {
    List<Node> nodes = new ArrayList<>();
    for (HttpHost httpHost : httpHosts(hostAddresses)) {
      nodes.add(new Node(httpHost));
    }
    client.setNodes(nodes);
  }

  private static HttpHost[] httpHosts(String... hostAddresses) {
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
    return "io.opentelemetry.opensearch-rest-1.0";
  }
}
