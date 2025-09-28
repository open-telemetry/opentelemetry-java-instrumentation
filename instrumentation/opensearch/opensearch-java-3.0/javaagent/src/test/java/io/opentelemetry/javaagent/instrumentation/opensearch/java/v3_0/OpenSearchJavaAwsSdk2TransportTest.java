/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.AttributeMap;

@SuppressWarnings("deprecation") // using deprecated semconv
public class OpenSearchJavaAwsSdk2TransportTest extends AbstractOpenSearchJavaTest {

  protected static final MockWebServerExtension server = new MockWebServerExtension();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  @Override
  void setUp() throws Exception {
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
  void prepTest() {
    server.beforeTestExecution(null);

    // Mock OpenSearch cluster health response
    String healthResponse =
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

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, healthResponse));

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, healthResponse));
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() throws Exception {
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
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception {
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

  @Test
  @Override
  void shouldGetStatusAsyncWithTraces() throws Exception {
    AtomicReference<CompletableFuture<HealthResponse>> responseCompletableFuture =
        new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    getTesting()
        .runWithSpan(
            "client",
            () -> {
              CompletableFuture<HealthResponse> future = openSearchAsyncClient.cluster().health();
              responseCompletableFuture.set(future);

              future.whenComplete(
                  (response, throwable) -> {
                    getTesting()
                        .runWithSpan(
                            "callback",
                            () -> {
                              if (throwable != null) {
                                throw new RuntimeException(throwable);
                              }
                              countDownLatch.countDown();
                            });
                  });
            });

    countDownLatch.await();
    HealthResponse healthResponse = responseCompletableFuture.get().get();
    assertThat(healthResponse).isNotNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("client").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health")),
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
                                equalTo(
                                    NETWORK_PEER_ADDRESS,
                                    httpHost.getHost()), // Netty 4.1 Instrumentation collects
                                // NETWORK_PEER_ADDRESS
                                equalTo(
                                    NETWORK_PEER_PORT,
                                    httpHost.getPort()), // Netty 4.1 Instrumentation collects
                                // NETWORK_PEER_PORT
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L)),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }
}
