/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.AttributeMap;

@SuppressWarnings("deprecation") // using deprecated semconv
class OpenSearchAwsSdk2TransportTest extends AbstractOpenSearchTest {

  private static final MockWebServerExtension server = new MockWebServerExtension();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));
  private static final String HEALTH_RESPONSE =
      "{\n"
          + "  \"cluster_name\": \"test-cluster\",\n"
          + "  \"status\": \"green\",\n"
          + "  \"timed_out\": false,\n"
          + "  \"number_of_nodes\": 1,\n"
          + "  \"number_of_data_nodes\": 1,\n"
          + "  \"active_primary_shards\": 0,\n"
          + "  \"active_shards\": 0,\n"
          + "  \"relocating_shards\": 0,\n"
          + "  \"initializing_shards\": 0,\n"
          + "  \"unassigned_shards\": 0,\n"
          + "  \"delayed_unassigned_shards\": 0,\n"
          + "  \"number_of_pending_tasks\": 0,\n"
          + "  \"number_of_in_flight_fetch\": 0,\n"
          + "  \"task_max_waiting_in_queue_millis\": 0,\n"
          + "  \"active_shards_percent_as_number\": 100.0\n"
          + "}";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  @Override
  void setUp() {
    server.start();
    openSearchClient = buildOpenSearchClient();
    openSearchAsyncClient = buildOpenSearchAsyncClient();
    httpHost = server.httpsUri();
  }

  @AfterAll
  @Override
  void tearDown() {
    server.stop();
  }

  @BeforeEach
  void setupForHealthResponse() {
    server.beforeTestExecution(null);

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, HEALTH_RESPONSE));

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, HEALTH_RESPONSE));
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() {
    SdkHttpClient httpClient =
        ApacheHttpClient.builder()
            .buildWithDefaults(
                AttributeMap.builder()
                    .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                    .build());

    AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            server.httpsUri().toString().replace("https://", ""),
            Region.AP_NORTHEAST_1,
            AwsSdk2TransportOptions.builder().setCredentials(CREDENTIALS_PROVIDER).build());

    return new OpenSearchClient(transport);
  }

  @Override
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() {
    SdkAsyncHttpClient httpClient =
        NettyNioAsyncHttpClient.builder()
            .buildWithDefaults(
                AttributeMap.builder()
                    .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                    .build());

    AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            server.httpsUri().toString().replace("https://", ""),
            Region.AP_NORTHEAST_1,
            AwsSdk2TransportOptions.builder().setCredentials(CREDENTIALS_PROVIDER).build());

    return new OpenSearchAsyncClient(transport);
  }

  @ParameterizedTest
  @MethodSource("bareAuthorityCases")
  void bareAuthorityUsesHttpsPortDefaults(String authority, Integer expectedPort)
      throws IOException {
    AwsSdk2Transport transport =
        new AwsSdk2Transport(
            successfulHttpClient(),
            authority,
            Region.AP_NORTHEAST_1,
            AwsSdk2TransportOptions.builder().setCredentials(CREDENTIALS_PROVIDER).build());
    cleanup.deferCleanup(transport);
    OpenSearchClient client = new OpenSearchClient(transport);

    HealthResponse healthResponse = client.cluster().health();
    assertThat(healthResponse).isNotNull();

    List<AttributeAssertion> assertions = databaseAttributes("GET", "GET /_cluster/health");
    assertions.add(equalTo(NETWORK_PEER_ADDRESS, null));
    assertions.add(equalTo(NETWORK_PEER_PORT, null));
    assertions.add(equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? "os.example" : null));
    assertions.add(
        equalTo(
            SERVER_PORT,
            emitStableDatabaseSemconv() && expectedPort != null
                ? Long.valueOf(expectedPort)
                : null));

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                openSearchSpanName(
                                    "GET",
                                    "os.example",
                                    expectedPort == null ? null : Long.valueOf(expectedPort)))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(assertions)));
  }

  private static Stream<Arguments> bareAuthorityCases() {
    return Stream.of(
        argumentSet("HTTPS default port", "os.example:443", null),
        argumentSet("non-default port", "os.example:9200", 9200));
  }

  private static SdkHttpClient successfulHttpClient() {
    return new SdkHttpClient() {
      @Override
      public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
        return new ExecutableHttpRequest() {
          @Override
          public HttpExecuteResponse call() {
            return HttpExecuteResponse.builder()
                .response(
                    SdkHttpResponse.builder()
                        .statusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .build())
                .responseBody(
                    AbortableInputStream.create(
                        new ByteArrayInputStream(HEALTH_RESPONSE.getBytes(UTF_8))))
                .build();
          }

          @Override
          public void abort() {}
        };
      }

      @Override
      public void close() {}
    };
  }

  @Test
  @Override
  void shouldGetStatusAsyncWithTraces() throws Exception {
    CountDownLatch countDownLatch = new CountDownLatch(1);

    CompletableFuture<HealthResponse> responseCompletableFuture =
        getTesting()
            .runWithSpan(
                "client",
                () ->
                    openSearchAsyncClient
                        .cluster()
                        .health()
                        .whenComplete(
                            (response, throwable) ->
                                getTesting().runWithSpan("callback", countDownLatch::countDown)));

    countDownLatch.await();
    HealthResponse healthResponse = responseCompletableFuture.get();
    assertThat(healthResponse).isNotNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("client").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(openSearchSpanName("GET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(clusterHealthAttributes()),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                equalTo(SERVER_ADDRESS, httpHost.getHost()),
                                equalTo(SERVER_PORT, httpHost.getPort()),
                                equalTo(HTTP_REQUEST_METHOD, "GET"),
                                equalTo(URL_FULL, httpHost + "/_cluster/health"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                // Netty 4.1 Instrumentation collects NETWORK_PEER_ADDRESS
                                equalTo(NETWORK_PEER_ADDRESS, httpHost.getHost()),
                                // Netty 4.1 Instrumentation collects NETWORK_PEER_PORT
                                equalTo(NETWORK_PEER_PORT, httpHost.getPort()),
                                equalTo(maybeStablePeerService(), "test-peer-service")),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }
}
