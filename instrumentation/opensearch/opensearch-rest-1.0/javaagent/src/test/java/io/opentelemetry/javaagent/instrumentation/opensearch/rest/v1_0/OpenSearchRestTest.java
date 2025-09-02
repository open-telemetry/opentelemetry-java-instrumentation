/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v1_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.AbstractOpenSearchRestTest;
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
  protected RestClient buildRestClient() throws Exception {
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY,
        new UsernamePasswordCredentials(opensearch.getUsername(), opensearch.getPassword()));

    SSLContext sslContext =
        SSLContextBuilder.create().loadTrustMaterial(null, new TrustAllStrategy()).build();

    HttpHost httpHost = HttpHost.create(opensearch.getHttpHostAddress());
    return RestClient.builder(httpHost)
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
  protected int getResponseStatus(Response response) {
    return response.getStatusLine().getStatusCode();
  }
}
