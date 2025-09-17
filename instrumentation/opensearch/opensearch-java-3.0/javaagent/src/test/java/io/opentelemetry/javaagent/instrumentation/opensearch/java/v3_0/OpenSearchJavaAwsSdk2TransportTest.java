package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static org.assertj.core.api.Assertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // AwsSdk2Transport is the correct way for OpenSearch Java 3.0 with AWS SDK 2
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
    String healthResponse = "{\n" +
        "  \"cluster_name\": \"test-cluster\",\n" +
        "  \"status\": \"green\",\n" +
        "  \"timed_out\": false,\n" +
        "  \"number_of_nodes\": 1,\n" +
        "  \"number_of_data_nodes\": 1,\n" +
        "  \"active_primary_shards\": 0,\n" +
        "  \"active_shards\": 0,\n" +
        "  \"relocating_shards\": 0,\n" +
        "  \"initializing_shards\": 0,\n" +
        "  \"unassigned_shards\": 0,\n" +
        "  \"delayed_unassigned_shards\": 0,\n" +
        "  \"number_of_pending_tasks\": 0,\n" +
        "  \"number_of_in_flight_fetch\": 0,\n" +
        "  \"task_max_waiting_in_queue_millis\": 0,\n" +
        "  \"active_shards_percent_as_number\": 100.0\n" +
        "}";

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, healthResponse));

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.JSON_UTF_8, healthResponse));
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Override
  protected OpenSearchClient buildOpenSearchClient() throws Exception {
    SdkHttpClient httpClient = ApacheHttpClient.builder()
        .buildWithDefaults(AttributeMap.builder()
          .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
        .build());

    AwsSdk2Transport transport = new AwsSdk2Transport(
        httpClient,
        server.httpsUri().toString().replace("https://", ""),
        Region.AP_NORTHEAST_1,
        AwsSdk2TransportOptions.builder().setCredentials(CREDENTIALS_PROVIDER).build()
    );

    return new OpenSearchClient(transport);
  }

  @Override
  protected OpenSearchAsyncClient buildOpenSearchAsyncClient() throws Exception {
    SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
        .buildWithDefaults(AttributeMap.builder()
            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
            .build());

    AwsSdk2Transport transport = new AwsSdk2Transport(
        httpClient,
        server.httpsUri().toString().replace("https://", ""),
        Region.AP_NORTHEAST_1,
        AwsSdk2TransportOptions.builder().setCredentials(CREDENTIALS_PROVIDER).build()
    );

    return new OpenSearchAsyncClient(transport);
  }

  @Test
  @Override
  void shouldGetStatusWithTraces() throws IOException {
    HealthResponse healthResponse = openSearchClient.cluster().health();
    assertThat(healthResponse).isNotNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(maybeStable(DB_SYSTEM), "opensearch"),
                                equalTo(maybeStable(DB_OPERATION), "GET"),
                                equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health"))));
  }

  @Test
  @Override
  void shouldGetStatusAsyncWithTraces() throws Exception {
    AtomicReference<CompletableFuture<HealthResponse>> responseCompletableFuture =
        new AtomicReference<>();

    getTesting()
        .runWithSpan(
            "client",
            () -> responseCompletableFuture.set(openSearchAsyncClient.cluster().health()));
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
                                equalTo(maybeStable(DB_STATEMENT), "GET /_cluster/health"))));
  }
}
