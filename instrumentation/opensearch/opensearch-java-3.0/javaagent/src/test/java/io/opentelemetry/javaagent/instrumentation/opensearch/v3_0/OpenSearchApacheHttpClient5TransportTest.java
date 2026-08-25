/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
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
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

class OpenSearchApacheHttpClient5TransportTest extends AbstractOpenSearchTest {

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
    OpenSearchClient nodeListClient =
        new OpenSearchClient(buildTransport(configuredHost(), hostThatIsDown()));

    HealthResponse healthResponse = nodeListClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    assertNodeListTarget();
  }

  private HttpHost configuredHost() {
    return new HttpHost("https", httpHost.getHost(), httpHost.getPort());
  }

  private HttpHost hostThatIsDown() {
    // nothing listens on this port, so the request is served by the running server after a retry
    return new HttpHost("https", httpHost.getHost(), httpHost.getPort() + 1);
  }

  private OpenSearchTransport buildTransport(HttpHost... hosts) throws Exception {
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

    return ApacheHttpClient5TransportBuilder.builder(hosts)
        .setHttpClientConfigCallback(
            httpClientBuilder ->
                httpClientBuilder
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(credentialsProvider))
        .build();
  }

  @Test
  void shouldRecordErrorType() {
    assertThatThrownBy(
            () ->
                openSearchClient.get(
                    request -> request.index("invalid-index").id("1"), Object.class))
        .isInstanceOf(OpenSearchException.class);

    assertErrorTypeSpan();
  }

  @Test
  void shouldRecordAsyncErrorType() {
    assertThatThrownBy(
            () ->
                openSearchAsyncClient
                    .get(request -> request.index("invalid-index").id("1"), Object.class)
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(OpenSearchException.class);

    assertErrorTypeSpan();
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private void assertErrorTypeSpan() {
    List<AttributeAssertion> assertions =
        new ArrayList<>(
            asList(
                equalTo(maybeStable(DB_SYSTEM), OPENSEARCH),
                equalTo(maybeStable(DB_OPERATION), "GET"),
                equalTo(maybeStable(DB_STATEMENT), "GET /invalid-index/_doc/1")));
    if (emitStableDatabaseSemconv()) {
      assertions.add(equalTo(ERROR_TYPE, "404"));
    }

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(assertions),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }
}
