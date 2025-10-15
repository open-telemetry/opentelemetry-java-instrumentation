/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TelemetryRetriever;
import io.opentelemetry.smoketest.TelemetryRetrieverProvider;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
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
@SuppressWarnings({"deprecation"})
@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class KafkaConnectSinkTaskBaseTest implements TelemetryRetrieverProvider {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      SmokeTestInstrumentationExtension.create();

  // Using the same fake backend pattern as smoke tests (with ARM64 support)
  protected static GenericContainer<?> backend;
  protected static TelemetryRetriever telemetryRetriever;

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
  protected static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  // Docker network / containers
  protected static Network network;
  protected static FixedHostPortGenericContainer<?> kafka;
  protected static GenericContainer<?> zookeeper;
  protected static GenericContainer<?> kafkaConnect;
  protected static int kafkaExposedPort;

  protected static OpenTelemetrySdk openTelemetry;

  // Abstract methods for database-specific setup
  protected abstract void setupDatabaseContainer();

  protected abstract void startDatabaseContainer();

  protected abstract void stopDatabaseContainer();

  protected abstract void clearDatabaseData() throws Exception;

  protected abstract String getConnectorInstallCommand();

  protected abstract String getConnectorName();

  // Static methods
  protected static String getKafkaConnectUrl() {
    return format(
        Locale.ROOT,
        "http://%s:%s",
        kafkaConnect.getHost(),
        kafkaConnect.getMappedPort(CONNECT_REST_PORT_INTERNAL));
  }

  protected static String getInternalKafkaBoostrapServers() {
    return KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT;
  }

  protected static String getKafkaBoostrapServers() {
    return kafka.getHost() + ":" + kafkaExposedPort;
  }

  @Override
  public TelemetryRetriever getTelemetryRetriever() {
    return telemetryRetriever;
  }

  @BeforeAll
  public void setupBase() {
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

  private static void setupZookeeper() {
    zookeeper =
        new GenericContainer<>("confluentinc/cp-zookeeper:" + CONFLUENT_VERSION)
            .withNetwork(network)
            .withNetworkAliases(ZOOKEEPER_NETWORK_ALIAS)
            .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(ZOOKEEPER_INTERNAL_PORT))
            .withEnv("ZOOKEEPER_TICK_TIME", "2000")
            .withExposedPorts(ZOOKEEPER_INTERNAL_PORT)
            .withStartupTimeout(Duration.of(5, MINUTES));
  }

  private static void setupKafka() {
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
            // Save logs to desktop
            .withFileSystemBind(
                System.getProperty("user.home") + "/Desktop/kafka-connect-logs",
                "/var/log/kafka-connect",
                BindMode.READ_WRITE)
            // Copy the agent jar to the container
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), "/opentelemetry-javaagent.jar")
            // Configure the agent to export spans to backend (like smoke tests)
            .withEnv(
                "JAVA_TOOL_OPTIONS",
                "-javaagent:/opentelemetry-javaagent.jar " + "-Dotel.javaagent.debug=true")
            // Disable test exporter and force OTLP exporter
            .withEnv("OTEL_TESTING_EXPORTER_ENABLED", "false")
            .withEnv("OTEL_TRACES_EXPORTER", "otlp")
            .withEnv("OTEL_METRICS_EXPORTER", "none")
            .withEnv("OTEL_LOGS_EXPORTER", "none")
            .withEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://" + BACKEND_ALIAS + ":" + BACKEND_PORT)
            .withEnv("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
            .withEnv("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")
            .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10ms")
            .withEnv("OTEL_METRIC_EXPORT_INTERVAL", "1000")
            .withEnv("CONNECT_BOOTSTRAP_SERVERS", getInternalKafkaBoostrapServers())
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

  @BeforeEach
  public void resetBase() throws Exception {
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
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
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
  public void cleanupBase() {
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

  protected static Producer<String, String> instrument(Producer<String, String> producer) {
    return KafkaTelemetry.create(openTelemetry).wrap(producer);
  }
}
