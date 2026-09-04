/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
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
  void configuredNodeListIsSortedAndPreservesDuplicates() throws Exception {
    OpenSearchTransport transport =
        buildTransport(hostThatIsDown(), configuredHost(), configuredHost());
    cleanup.deferCleanup(transport);
    OpenSearchClient nodeListClient = new OpenSearchClient(transport);

    HealthResponse healthResponse = nodeListClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    assertNodeListTarget(
        httpHost.getHost()
            + ":"
            + httpHost.getPort()
            + ","
            + httpHost.getHost()
            + ":"
            + httpHost.getPort()
            + ","
            + httpHost.getHost()
            + ":61");
  }

  @Test
  void configuredNodeListIsSortedWhenEffectivePortsDiffer() throws Exception {
    OpenSearchTransport transport =
        buildTransport(hostWithDefaultPortThatIsDown(), configuredHost());
    cleanup.deferCleanup(transport);
    OpenSearchClient nodeListClient = new OpenSearchClient(transport);

    HealthResponse healthResponse = nodeListClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    assertNodeListTarget(
        httpHost.getHost() + ":443," + httpHost.getHost() + ":" + httpHost.getPort());
  }

  @Test
  void configuredNodeListIsSorted() throws Exception {
    OpenSearchTransport transport = buildTransport(configuredHosts(5));
    cleanup.deferCleanup(transport);
    OpenSearchClient nodeListClient = new OpenSearchClient(transport);

    HealthResponse healthResponse = nodeListClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    assertNodeListTarget(configuredTarget(5));
  }

  @Test
  void configuredNodeListSortsBeforeCapping() throws Exception {
    OpenSearchTransport transport = buildTransport(configuredHosts(6));
    cleanup.deferCleanup(transport);
    OpenSearchClient nodeListClient = new OpenSearchClient(transport);

    HealthResponse healthResponse = nodeListClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    assertNodeListTarget(configuredTarget(6));
  }

  private HttpHost configuredHost() {
    return new HttpHost("https", httpHost.getHost(), httpHost.getPort());
  }

  private HttpHost hostWithDefaultPortThatIsDown() {
    return new HttpHost("https", httpHost.getHost(), -1);
  }

  private HttpHost hostThatIsDown() {
    // nothing listens on this port, so the request is served by the running server after a retry
    return new HttpHost("https", httpHost.getHost(), 61);
  }

  private HttpHost[] configuredHosts(int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(index -> new HttpHost("https", nodeName(index), httpHost.getPort()))
        .toArray(HttpHost[]::new);
  }

  private String configuredTarget(int count) {
    return IntStream.rangeClosed(1, Math.min(count, 5))
        .mapToObj(index -> nodeName(count - index + 1) + ":" + httpHost.getPort())
        .collect(joining(","));
  }

  private static String nodeName(int index) {
    return "node-" + (char) ('z' - index + 1) + ".example";
  }

  private OpenSearchTransport buildTransport(HttpHost... hosts) throws Exception {
    TrustStrategy acceptingTrustStrategy = (certificate, authType) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    ClientTlsStrategyBuilder tlsStrategyBuilder =
        ClientTlsStrategyBuilder.create()
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .setSslContext(sslContext);
    tlsStrategyBuilder.setHostnameVerificationPolicy(HostnameVerificationPolicy.CLIENT);
    TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
    DnsResolver dnsResolver =
        new DnsResolver() {
          @Override
          public InetAddress[] resolve(String host) throws UnknownHostException {
            return InetAddress.getAllByName(httpHost.getHost());
          }

          @Override
          public String resolveCanonicalHostname(String host) {
            return host;
          }
        };
    PoolingAsyncClientConnectionManager connectionManager =
        PoolingAsyncClientConnectionManagerBuilder.create()
            .setTlsStrategy(tlsStrategy)
            .setDnsResolver(dnsResolver)
            .build();

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

  private void assertErrorTypeSpan() {
    List<AttributeAssertion> databaseAssertions =
        databaseAttributes("GET", "GET /invalid-index/_doc/1");
    List<AttributeAssertion> assertions =
        withServer(databaseAssertions.toArray(new AttributeAssertion[0]));
    if (emitStableDatabaseSemconv()) {
      assertions.add(equalTo(ERROR_TYPE, "404"));
    }

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(openSearchSpanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(assertions),
                    span ->
                        span.hasName("GET").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }
}
