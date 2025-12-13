/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

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
import static io.opentelemetry.semconv.incubating.PeerIncubatingAttributes.PEER_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
class OpenSearchAwsSdk2TransportTest extends AbstractOpenSearchTest {

  protected static final MockWebServerExtension server = new MockWebServerExtension();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

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

  void setupForHealthResponse() {
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

  void setupForSearchResponse() {
    server.beforeTestExecution(null); // Added this line

    // Mock OpenSearch Search response, matching the TestDocument class structure
    String searchResponseJson =
        "{\n"
            + "  \"took\": 5,\n"
            + "  \"timed_out\": false,\n"
            + "  \"_shards\": {\n"
            + "    \"total\": 1,\n"
            + "    \"successful\": 1,\n"
            + "    \"skipped\": 0,\n"
            + "    \"failed\": 0\n"
            + "  },\n"
            + "  \"hits\": {\n"
            + "    \"total\": {\n"
            + "      \"value\": 1,\n"
            + "      \"relation\": \"eq\"\n"
            + "    },\n"
            + "    \"max_score\": 1.0,\n"
            + "    \"hits\": [\n"
            + "      {\n"
            + "        \"_index\": \"my_index\",\n"
            + "        \"_id\": \"1\",\n"
            + "        \"_score\": 1.0,\n"
            + "        \"_source\": {\n"
            + "          \"id\": \"doc-1\",\n" // Corrected field
            + "          \"message\": \"This is a test document.\"\n" // Corrected field
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, searchResponseJson));
  }

  void setupForMsearchResponse() {
    server.beforeTestExecution(null);

    String msearchResponseJson =
        "{\n"
            + "  \"took\": 17,\n"
            + "  \"responses\": [\n"
            + "    {\n"
            + "      \"took\": 4,\n"
            + "      \"timed_out\": false,\n"
            + "      \"_shards\": {\"total\": 5, \"successful\": 5, \"skipped\": 0, \"failed\": 0},\n"
            + "      \"hits\": {\n"
            + "        \"total\": { \"value\": 2, \"relation\": \"eq\" },\n"
            + "        \"max_score\": 1.0,\n"
            + "        \"hits\": [\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-1-1\",\n"
            + "            \"_score\": 1.0,\n"
            + "            \"_source\": {\"id\": \"doc-1-1\", \"message\": \"message for search 1 hit 1\"}\n"
            + "          },\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-1-2\",\n"
            + "            \"_score\": 0.95,\n"
            + "            \"_source\": {\"id\": \"doc-1-2\", \"message\": \"message for search 1 hit 2\"}\n"
            + "          }\n"
            + "        ]\n"
            + "      },\n"
            + "      \"status\": 200\n"
            + "    },\n"
            + "    {\n"
            + "      \"took\": 7,\n"
            + "      \"timed_out\": false,\n"
            + "      \"_shards\": {\"total\": 3, \"successful\": 3, \"skipped\": 0, \"failed\": 0},\n"
            + "      \"hits\": {\n"
            + "        \"total\": { \"value\": 2, \"relation\": \"eq\" },\n"
            + "        \"max_score\": 1.0,\n"
            + "        \"hits\": [\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-2-1\",\n"
            + "            \"_score\": 1.0,\n"
            + "            \"_source\": {\"id\": \"doc-2-1\", \"message\": \"message for search 2 hit 1\"}\n"
            + "          },\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-2-2\",\n"
            + "            \"_score\": 0.9,\n"
            + "            \"_source\": {\"id\": \"doc-2-2\", \"message\": \"message for search 2 hit 2\"}\n"
            + "          }\n"
            + "        ]\n"
            + "      },\n"
            + "      \"status\": 200\n"
            + "    },\n"
            + "    {\n"
            + "      \"took\": 6,\n"
            + "      \"timed_out\": false,\n"
            + "      \"_shards\": {\"total\": 4, \"successful\": 4, \"skipped\": 0, \"failed\": 0},\n"
            + "      \"hits\": {\n"
            + "        \"total\": { \"value\": 2, \"relation\": \"eq\" },\n"
            + "        \"max_score\": 1.0,\n"
            + "        \"hits\": [\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-3-1\",\n"
            + "            \"_score\": 1.0,\n"
            + "            \"_source\": {\"id\": \"doc-3-1\", \"message\": \"message for search 3 hit 1\"}\n"
            + "          },\n"
            + "          {\n"
            + "            \"_index\": \"my_index\",\n"
            + "            \"_id\": \"doc-3-2\",\n"
            + "            \"_score\": 0.8,\n"
            + "            \"_source\": {\"id\": \"doc-3-2\", \"message\": \"message for search 3 hit 2\"}\n"
            + "          }\n"
            + "        ]\n"
            + "      },\n"
            + "      \"status\": 200\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, msearchResponseJson));
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

  @Test
  @Override
  void shouldGetStatusWithTraces() throws IOException {
    setupForHealthResponse();
    super.shouldGetStatusWithTraces();
  }

  @Test
  @Override
  void shouldGetStatusAsyncWithTraces() throws Exception {
    setupForHealthResponse();
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
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200L),
                                equalTo(PEER_SERVICE, "test-peer-service"),
                                equalTo(NETWORK_PEER_ADDRESS, server.httpsEndpoint().host()),
                                equalTo(NETWORK_PEER_PORT, server.httpsPort())),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(1))));
  }

  @Test
  @Override
  void shouldRecordMetrics() throws IOException {
    setupForHealthResponse();
    super.shouldRecordMetrics();
  }

  @Test
  @Override
  void shouldCaptureSearchQueryBody() throws IOException {
    // Execute search query with body
    setupForSearchResponse();
    super.shouldCaptureSearchQueryBody();
  }

  @Test
  @Override
  void shouldCaptureMsearchQueryBody() throws IOException {
    // Execute search query with body
    setupForMsearchResponse();
    super.shouldCaptureMsearchQueryBody();
  }
}
