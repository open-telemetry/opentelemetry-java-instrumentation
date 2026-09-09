/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetrics;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertProcessMetricsWithConsumedMessages;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertReceiveMetrics;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.groupTraces;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PROCESS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TelemetryRetriever;
import io.opentelemetry.smoketest.TelemetryRetrieverProvider;
import io.restassured.http.ContentType;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

// Suppressing warnings for test dependencies and deprecated Testcontainers API
@SuppressWarnings("deprecation")
@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class KafkaConnectSinkTaskBaseTest implements TelemetryRetrieverProvider {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      SmokeTestInstrumentationExtension.create();

  // Using the same fake backend pattern as smoke tests (with ARM64 support)
  protected GenericContainer<?> backend;
  protected TelemetryRetriever telemetryRetriever;

  protected static final String CONFLUENT_VERSION = "7.5.9";

  // Ports
  protected static final int KAFKA_INTERNAL_PORT = 9092;
  protected static final int ZOOKEEPER_INTERNAL_PORT = 2181;
  protected static final int KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT = 29092;
  protected static final int CONNECT_REST_PORT_INTERNAL = 8083;

  // Network Aliases
  protected static final String KAFKA_NETWORK_ALIAS = "kafka";
  protected static final String ZOOKEEPER_NETWORK_ALIAS = "zookeeper";
  protected static final String KAFKA_CONNECT_NETWORK_ALIAS = "kafka-connect";
  protected static final String BACKEND_ALIAS = "backend";
  protected static final int BACKEND_PORT = 8080;

  // Other constants
  protected static final String PLUGIN_PATH_CONTAINER = "/usr/share/java";
  protected static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // Docker network / containers
  protected Network network;
  protected FixedHostPortGenericContainer<?> kafka;
  protected GenericContainer<?> zookeeper;
  protected GenericContainer<?> kafkaConnect;
  protected int kafkaExposedPort;

  protected OpenTelemetrySdk openTelemetry;

  @TempDir static Path kafkaConnectLogsDir;

  // Abstract methods for database-specific setup
  protected abstract void setupDatabaseContainer();

  protected abstract void startDatabaseContainer();

  protected abstract void stopDatabaseContainer();

  protected abstract void clearDatabaseData() throws Exception;

  protected abstract String getConnectorInstallCommand();

  protected abstract String getConnectorName();

  // Static methods
  protected String getKafkaConnectUrl() {
    return format(
        Locale.ROOT,
        "http://%s:%s",
        kafkaConnect.getHost(),
        kafkaConnect.getMappedPort(CONNECT_REST_PORT_INTERNAL));
  }

  protected String getInternalKafkaBootstrapServers() {
    return KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT;
  }

  protected String getKafkaBootstrapServers() {
    return kafka.getHost() + ":" + kafkaExposedPort;
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  protected final void waitAndAssertRelevantTraces(Consumer<TraceAssert>... assertions) {
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = groupTraces(testing.spans());
              // Stable receive spans are separate traces, and Kafka Connect writes status records
              // on its own schedule. Neither is relevant to the sink-task assertions.
              if (emitStableMessagingSemconv()) {
                traces.removeIf(
                    trace ->
                        trace.size() == 1
                            && trace.get(0).getKind() == SpanKind.CLIENT
                            && (trace.get(0).getName().equals("poll")
                                || trace.get(0).getName().startsWith("poll ")));
              }
              traces.removeIf(
                  trace ->
                      trace.stream()
                          .anyMatch(span -> span.getName().contains("kafka-connect-status")));
              // Kafka Connect polls its own REST API on a schedule that is not deterministic
              // relative to the test, so these traces are not relevant to the assertions here.
              traces.removeIf(
                  trace -> trace.size() == 1 && trace.get(0).getName().equals("GET /connectors"));
              TracesAssert.assertThat(traces).hasTracesSatisfyingExactly(asList(assertions));
            });
  }

  protected final void waitAndAssertMultiTopicTraces(
      Map<String, String> expectedKeysByDestination,
      Consumer<List<List<SpanData>>> processTraceAssertions) {
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = groupTraces(testing.spans());
              List<List<SpanData>> producerTraces = new ArrayList<>();
              for (List<SpanData> trace : traces) {
                if (trace.get(0).getName().equals("parent")) {
                  producerTraces.add(trace);
                }
              }
              assertThat(producerTraces).hasSize(1);

              List<SpanData> producerTrace = producerTraces.get(0);
              assertThat(producerTrace).hasSize(expectedKeysByDestination.size() + 1);
              Map<String, Attributes> expectedRecordAttributesBySpan = new HashMap<>();
              for (Map.Entry<String, String> expected : expectedKeysByDestination.entrySet()) {
                List<SpanData> matchingProducers = new ArrayList<>();
                for (SpanData span : producerTrace) {
                  if (span.getKind() == SpanKind.PRODUCER
                      && expected
                          .getKey()
                          .equals(span.getAttributes().get(MESSAGING_DESTINATION_NAME))) {
                    matchingProducers.add(span);
                  }
                }
                assertThat(matchingProducers).hasSize(1);
                SpanData producer = matchingProducers.get(0);
                assertThat(producer.getAttributes().get(MESSAGING_KAFKA_MESSAGE_KEY))
                    .isEqualTo(expected.getValue());
                expectedRecordAttributesBySpan.put(
                    spanId(producer.getSpanContext()),
                    recordAttributes(expected.getKey(), expected.getValue()));
              }

              List<List<SpanData>> processTraces = new ArrayList<>();
              for (List<SpanData> trace : traces) {
                SpanData root = trace.get(0);
                if (root.getKind() != SpanKind.CONSUMER
                    || !root.getInstrumentationScopeInfo()
                        .getName()
                        .equals("io.opentelemetry.kafka-connect-2.6")) {
                  continue;
                }
                for (LinkData link : root.getLinks()) {
                  if (expectedRecordAttributesBySpan.containsKey(spanId(link.getSpanContext()))) {
                    processTraces.add(trace);
                    break;
                  }
                }
              }
              assertThat(processTraces).isNotEmpty();

              List<String> linkedSpanIds = new ArrayList<>();
              List<Attributes> actualRecordAttributes = new ArrayList<>();
              for (List<SpanData> trace : processTraces) {
                SpanData process = trace.get(0);
                assertThat(process.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT))
                    .isEqualTo((long) process.getLinks().size());
                for (LinkData link : process.getLinks()) {
                  String linkedSpanId = spanId(link.getSpanContext());
                  assertThat(expectedRecordAttributesBySpan).containsKey(linkedSpanId);
                  linkedSpanIds.add(linkedSpanId);
                  if (emitStableMessagingSemconv()) {
                    assertThat(link.getAttributes().asMap().keySet())
                        .isSubsetOf(
                            MESSAGING_DESTINATION_NAME,
                            MESSAGING_DESTINATION_PARTITION_ID,
                            MESSAGING_KAFKA_OFFSET,
                            MESSAGING_KAFKA_MESSAGE_KEY);
                    actualRecordAttributes.add(effectiveRecordAttributes(process, link));
                  } else {
                    assertThat(link.getAttributes()).isEqualTo(Attributes.empty());
                  }
                }
              }

              assertThat(linkedSpanIds)
                  .containsExactlyInAnyOrderElementsOf(expectedRecordAttributesBySpan.keySet());
              if (emitStableMessagingSemconv()) {
                assertThat(actualRecordAttributes)
                    .containsExactlyInAnyOrderElementsOf(expectedRecordAttributesBySpan.values());
              }
              processTraceAssertions.accept(processTraces);
            });
  }

  private static Attributes effectiveRecordAttributes(SpanData process, LinkData link) {
    return Attributes.builder()
        .put(MESSAGING_DESTINATION_NAME, batchAttribute(process, link, MESSAGING_DESTINATION_NAME))
        .put(
            MESSAGING_DESTINATION_PARTITION_ID,
            batchAttribute(process, link, MESSAGING_DESTINATION_PARTITION_ID))
        .put(MESSAGING_KAFKA_OFFSET, batchAttribute(process, link, MESSAGING_KAFKA_OFFSET))
        .put(
            MESSAGING_KAFKA_MESSAGE_KEY, batchAttribute(process, link, MESSAGING_KAFKA_MESSAGE_KEY))
        .build();
  }

  private static <T> T batchAttribute(
      SpanData process, LinkData link, AttributeKey<T> attributeKey) {
    T spanValue = process.getAttributes().get(attributeKey);
    T linkValue = link.getAttributes().get(attributeKey);
    if (linkValue != null) {
      assertThat(spanValue).isNull();
      return linkValue;
    }
    assertThat(spanValue).isNotNull();
    return spanValue;
  }

  private static Attributes recordAttributes(String destination, String messageKey) {
    return Attributes.builder()
        .put(MESSAGING_DESTINATION_NAME, destination)
        .put(MESSAGING_DESTINATION_PARTITION_ID, "0")
        .put(MESSAGING_KAFKA_OFFSET, 0)
        .put(MESSAGING_KAFKA_MESSAGE_KEY, messageKey)
        .build();
  }

  // the offset and the message key stay on the link even when the batch carries a single record,
  // because they are only recommended on spans that describe an operation on a single message
  protected static LinkData recordLink(SpanContext producerSpanContext, String messageKey) {
    if (!emitStableMessagingSemconv()) {
      return LinkData.create(producerSpanContext);
    }
    return LinkData.create(
        producerSpanContext,
        Attributes.builder()
            .put(MESSAGING_KAFKA_OFFSET, 0)
            .put(MESSAGING_KAFKA_MESSAGE_KEY, messageKey)
            .build());
  }

  private static String spanId(SpanContext spanContext) {
    return spanContext.getTraceId() + spanContext.getSpanId();
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }

  @BeforeAll
  void setupBase() {
    network = Network.newNetwork();

    // Start backend container first (like smoke tests)
    backend =
        new GenericContainer<>(
                DockerImageName.parse(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20250811.16876216352"))
            .withExposedPorts(BACKEND_PORT)
            .withNetwork(network)
            .withNetworkAliases(BACKEND_ALIAS)
            .waitingFor(
                Wait.forHttp("/health")
                    .forPort(BACKEND_PORT)
                    .withStartupTimeout(Duration.of(5, MINUTES)))
            .withStartupTimeout(Duration.of(5, MINUTES));
    backend.start();

    telemetryRetriever =
        new TelemetryRetriever(backend.getMappedPort(BACKEND_PORT), Duration.ofSeconds(30));

    openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                    .addSpanProcessor(
                        SimpleSpanProcessor.create(
                            OtlpGrpcSpanExporter.builder()
                                .setEndpoint(
                                    "http://localhost:" + backend.getMappedPort(BACKEND_PORT))
                                .build()))
                    .build())
            .setPropagators(
                ContextPropagators.create(
                    TextMapPropagator.composite(W3CTraceContextPropagator.getInstance())))
            .build();

    setupZookeeper();
    setupKafka();
    setupDatabaseContainer();
    setupKafkaConnect();

    // Start containers (backend already started)
    startDatabaseContainer();
    Startables.deepStart(Stream.of(zookeeper, kafka, kafkaConnect)).join();

    // Wait until Kafka Connect container is ready
    given()
        .contentType(ContentType.JSON)
        .when()
        .get(getKafkaConnectUrl())
        .then()
        .statusCode(HttpStatus.SC_OK);
  }

  private void setupZookeeper() {
    zookeeper =
        new GenericContainer<>("confluentinc/cp-zookeeper:" + CONFLUENT_VERSION)
            .withNetwork(network)
            .withNetworkAliases(ZOOKEEPER_NETWORK_ALIAS)
            .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(ZOOKEEPER_INTERNAL_PORT))
            .withEnv("ZOOKEEPER_TICK_TIME", "2000")
            .withExposedPorts(ZOOKEEPER_INTERNAL_PORT)
            .withStartupTimeout(Duration.of(5, MINUTES));
  }

  private void setupKafka() {
    String zookeeperInternalUrl = ZOOKEEPER_NETWORK_ALIAS + ":" + ZOOKEEPER_INTERNAL_PORT;

    kafkaExposedPort = PortUtils.findOpenPort();
    kafka =
        new FixedHostPortGenericContainer<>("confluentinc/cp-kafka:" + CONFLUENT_VERSION)
            .withFixedExposedPort(kafkaExposedPort, KAFKA_INTERNAL_PORT)
            .withNetwork(network)
            .withNetworkAliases(KAFKA_NETWORK_ALIAS)
            .withEnv("KAFKA_BROKER_ID", "1")
            .withEnv("KAFKA_ZOOKEEPER_CONNECT", zookeeperInternalUrl)
            .withEnv("ZOOKEEPER_SASL_ENABLED", "false")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv(
                "KAFKA_LISTENERS",
                "PLAINTEXT://0.0.0.0:"
                    + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT
                    + ",PLAINTEXT_HOST://0.0.0.0:"
                    + KAFKA_INTERNAL_PORT)
            .withEnv(
                "KAFKA_ADVERTISED_LISTENERS",
                "PLAINTEXT://"
                    + KAFKA_NETWORK_ALIAS
                    + ":"
                    + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT
                    + ",PLAINTEXT_HOST://localhost:"
                    + kafkaExposedPort)
            .withEnv(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT")
            .withEnv("KAFKA_SASL_ENABLED_MECHANISMS", "PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL", "PLAINTEXT")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_OPTS", "-Djava.net.preferIPv4Stack=True")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "100")
            .withStartupTimeout(Duration.of(5, MINUTES));
  }

  private void setupKafkaConnect() {
    // Get the agent path from system properties (smoke test pattern)
    String agentPath = System.getProperty("io.opentelemetry.smoketest.agent.shadowJar.path");
    if (agentPath == null) {
      throw new IllegalStateException(
          "Agent path not found. Make sure the shadowJar task is configured correctly.");
    }

    kafkaConnect =
        new GenericContainer<>("confluentinc/cp-kafka-connect:" + CONFLUENT_VERSION)
            .withNetwork(network)
            .withNetworkAliases(KAFKA_CONNECT_NETWORK_ALIAS)
            .withExposedPorts(CONNECT_REST_PORT_INTERNAL)
            .withLogConsumer(
                new Slf4jLogConsumer(LoggerFactory.getLogger("kafka-connect-container")))
            .withFileSystemBind(
                kafkaConnectLogsDir.toString(), "/var/log/kafka-connect", BindMode.READ_WRITE)
            // Copy the agent jar to the container
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent.jar")
            // Configure the agent to export spans to backend (like smoke tests)
            .withEnv("JAVA_TOOL_OPTIONS", javaToolOptions())
            // Disable test exporter and force OTLP exporter
            .withEnv("OTEL_TESTING_EXPORTER_ENABLED", "false")
            .withEnv("OTEL_TRACES_EXPORTER", "otlp")
            .withEnv("OTEL_METRICS_EXPORTER", "otlp")
            .withEnv("OTEL_LOGS_EXPORTER", "none")
            .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://" + BACKEND_ALIAS + ":" + BACKEND_PORT)
            .withEnv("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
            .withEnv("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")
            .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10ms")
            // The fake backend can clear retained OTLP payloads between tests, but it cannot reset
            // metric state in the separately running Java agent. Unlike the regular in-process
            // Java agent test harness, which can and frequently does reset captured telemetry and
            // metric state, cumulative exports can re-emit measurements from earlier tests. Delta
            // temporality makes clearing the backend meaningful.
            .withEnv("OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE", "delta")
            .withEnv("OTEL_METRIC_EXPORT_INTERVAL", "1000")
            .withEnv(
                "OTEL_SEMCONV_STABILITY_OPT_IN",
                emitStableMessagingSemconv()
                    ? "messaging"
                    : System.getProperty("otel.semconv-stability.opt-in"))
            .withEnv("CONNECT_BOOTSTRAP_SERVERS", getInternalKafkaBootstrapServers())
            .withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", KAFKA_CONNECT_NETWORK_ALIAS)
            .withEnv("CONNECT_PLUGIN_PATH", PLUGIN_PATH_CONTAINER)
            .withEnv(
                "CONNECT_LOG4J_LOGGERS", "org.reflections=ERROR,org.apache.kafka.connect=DEBUG")
            .withEnv("CONNECT_REST_PORT", String.valueOf(CONNECT_REST_PORT_INTERNAL))
            .withEnv("CONNECT_GROUP_ID", "kafka-connect-group")
            .withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "kafka-connect-configs")
            .withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "kafka-connect-offsets")
            .withEnv("CONNECT_STATUS_STORAGE_TOPIC", "kafka-connect-status")
            .withEnv("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("CONNECT_VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
            .withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
            .waitingFor(
                Wait.forHttp("/")
                    .forPort(CONNECT_REST_PORT_INTERNAL)
                    .withStartupTimeout(Duration.of(5, MINUTES)))
            .withStartupTimeout(Duration.of(5, MINUTES))
            .withCommand(
                "bash",
                "-c",
                "mkdir -p /var/log/kafka-connect && "
                    + getConnectorInstallCommand()
                    + " && "
                    + "echo 'Starting Kafka Connect with logging to /var/log/kafka-connect/' && "
                    + "/etc/confluent/docker/run 2>&1 | tee /var/log/kafka-connect/kafka-connect.log");
  }

  private static String javaToolOptions() {
    StringBuilder options =
        new StringBuilder("-javaagent:/opentelemetry-javaagent.jar -Dotel.javaagent.debug=true");
    appendSystemProperty(options, "otel.semconv-stability.preview");
    appendSystemProperty(
        options, "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    return options.toString();
  }

  private static void appendSystemProperty(StringBuilder options, String propertyName) {
    String value = System.getProperty(propertyName);
    if (value != null) {
      options.append(" -D").append(propertyName).append('=').append(value);
    }
  }

  // whether the kafka-clients receive operation is enabled for the consumer that Kafka Connect
  // uses internally: when it is, that receive operation owns the consumed-messages count for
  // each delivery, and the Connect process operation must not count it again.
  protected static boolean isReceiveTelemetryEnabled() {
    return Boolean.getBoolean(
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
  }

  // asserts the messaging metrics for a single-message delivery through the given destination,
  // covering both the default configuration, where the Connect process operation is the only
  // operation that observes the delivery, and the receive-telemetry-enabled configuration, where
  // the kafka-clients receive operation on the sink connector's own consumer owns the count.
  protected void assertConnectMessagingMetrics(String destination) {
    if (isReceiveTelemetryEnabled()) {
      assertProcessMetrics(
          testing, "io.opentelemetry.kafka-connect-2.6", destination, null, "0", 1, null);
      assertReceiveMetrics(
          testing,
          "io.opentelemetry.kafka-clients-0.11",
          destination,
          "connect-" + getConnectorName(),
          "0",
          1,
          1,
          null);
    } else {
      assertProcessMetricsWithConsumedMessages(
          testing, "io.opentelemetry.kafka-connect-2.6", destination, null, "0", 1, 1, null);
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  protected static AttributeAssertion[] processAttributes(String destination, long batchSize) {
    return new AttributeAssertion[] {
      equalTo(MESSAGING_BATCH_MESSAGE_COUNT, batchSize),
      equalTo(MESSAGING_DESTINATION_NAME, destination),
      equalTo(MESSAGING_DESTINATION_PARTITION_ID, emitStableMessagingSemconv() ? "0" : null),
      equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? PROCESS : null),
      equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? PROCESS : null),
      equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? PROCESS : null),
      equalTo(MESSAGING_SYSTEM, KAFKA),
      satisfies(THREAD_ID, AbstractLongAssert::isNotZero),
      satisfies(THREAD_NAME, AbstractStringAssert::isNotBlank)
    };
  }

  @BeforeEach
  void resetBase() throws Exception {
    deleteConnectorIfExists();
    clearDatabaseData();
  }

  protected void awaitForTopicCreation(String topicName) {
    try (AdminClient adminClient = createAdminClient()) {
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .until(() -> adminClient.listTopics().names().get().contains(topicName));
    }
  }

  protected AdminClient createAdminClient() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBootstrapServers());
    return KafkaAdminClient.create(properties);
  }

  protected void deleteConnectorIfExists() {
    given()
        .log()
        .headers()
        .contentType(ContentType.JSON)
        .when()
        .delete(getKafkaConnectUrl() + "/connectors/" + getConnectorName())
        .andReturn()
        .then()
        .log()
        .all();
  }

  @AfterAll
  void cleanupBase() {
    telemetryRetriever.close();
    openTelemetry.close();

    // Stop all containers in reverse order of startup to ensure clean shutdown
    if (kafkaConnect != null) {
      kafkaConnect.stop();
    }

    stopDatabaseContainer();

    if (kafka != null) {
      kafka.stop();
    }

    if (zookeeper != null) {
      zookeeper.stop();
    }

    if (backend != null) {
      backend.stop();
    }

    if (network != null) {
      network.close();
    }
  }

  protected Producer<String, String> instrument(Producer<String, String> producer) {
    return KafkaTelemetry.create(openTelemetry).wrap(producer);
  }
}
