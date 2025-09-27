/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.restassured.http.ContentType;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.base.VerifyException;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
// Suppressing warnings for test dependencies and deprecated Testcontainers API
@SuppressWarnings({"deprecation"})
class PostgresKafkaConnectSinkTaskTest {

  private static final Logger logger =
      LoggerFactory.getLogger(PostgresKafkaConnectSinkTaskTest.class);

  // Using the same fake backend pattern as smoke tests (with ARM64 support)
  private static GenericContainer<?> backend;

  private static final String CONFLUENT_VERSION = "7.5.9";

  // Ports
  private static final int KAFKA_INTERNAL_PORT = 9092;
  private static final int ZOOKEEPER_INTERNAL_PORT = 2181;
  private static final int KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT = 29092;
  private static final int CONNECT_REST_PORT_INTERNAL = 8083;

  // Network Aliases
  private static final String KAFKA_NETWORK_ALIAS = "kafka";
  private static final String ZOOKEEPER_NETWORK_ALIAS = "zookeeper";
  private static final String KAFKA_CONNECT_NETWORK_ALIAS = "kafka-connect";
  private static final String POSTGRES_NETWORK_ALIAS = "postgres";
  private static final String BACKEND_ALIAS = "backend";
  private static final int BACKEND_PORT = 8080;

  // Database
  private static final String DB_NAME = "test";
  private static final String DB_USERNAME = "postgres";
  private static final String DB_PASSWORD = "password";
  private static final String DB_TABLE_PERSON = "person";

  // Other constants
  private static final String PLUGIN_PATH_CONTAINER = "/usr/share/java";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final String CONNECTOR_NAME = "test-postgres-connector";
  private static final String TOPIC_NAME = "test-postgres-topic";

  // Docker network / containers
  private static Network network;
  private static FixedHostPortGenericContainer<?> kafka;
  private static GenericContainer<?> zookeeper;
  private static GenericContainer<?> kafkaConnect;
  private static PostgreSQLContainer<?> postgreSql;
  private static int kafkaExposedPort;

  private static AdminClient adminClient;

  // Static methods

  private static String getKafkaConnectUrl() {
    return format(
        Locale.ROOT,
        "http://%s:%s",
        kafkaConnect.getHost(),
        kafkaConnect.getMappedPort(CONNECT_REST_PORT_INTERNAL));
  }

  private static String getBackendUrl() {
    return format(
        Locale.ROOT, "http://%s:%d", backend.getHost(), backend.getMappedPort(BACKEND_PORT));
  }

  private static String getInternalKafkaBoostrapServers() {
    return KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT;
  }

  private static String getKafkaBoostrapServers() {
    return kafka.getHost() + ":" + kafkaExposedPort;
  }

  private static int getRandomFreePort() {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to allocate port", e);
    }
  }

  @BeforeAll
  public static void setup() throws IOException {

    // Create log directory on desktop
    File logDir = new File(System.getProperty("user.home") + "/Desktop/kafka-connect-logs");
    if (!logDir.exists()) {
      logDir.mkdirs();
    }

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
            .withStartupTimeout(Duration.of(5, MINUTES))
            .withEnv(
                "DOCKER_DEFAULT_PLATFORM",
                "linux/amd64"); // Force AMD64 for stability on ARM64 hosts

    backend.start();

    // Configure test JVM OTLP endpoint now that backend is running
    String backendEndpoint =
        format(
            Locale.ROOT,
            "http://%s:%d/v1/traces",
            backend.getHost(),
            backend.getMappedPort(BACKEND_PORT));
    System.setProperty("otel.exporter.otlp.traces.endpoint", backendEndpoint);

    zookeeper =
        new GenericContainer<>("confluentinc/cp-zookeeper:" + CONFLUENT_VERSION)
            .withNetwork(network)
            .withNetworkAliases(ZOOKEEPER_NETWORK_ALIAS)
            .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(ZOOKEEPER_INTERNAL_PORT))
            .withEnv("ZOOKEEPER_TICK_TIME", "2000")
            .withExposedPorts(ZOOKEEPER_INTERNAL_PORT)
            .withStartupTimeout(Duration.of(5, MINUTES));
    String zookeeperInternalUrl = ZOOKEEPER_NETWORK_ALIAS + ":" + ZOOKEEPER_INTERNAL_PORT;

    kafkaExposedPort = getRandomFreePort();
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
                    + "confluent-hub install --no-prompt --component-dir /usr/share/java "
                    + "confluentinc/kafka-connect-jdbc:10.7.4 && "
                    + "echo 'Starting Kafka Connect with logging to /var/log/kafka-connect/' && "
                    + "/etc/confluent/docker/run 2>&1 | tee /var/log/kafka-connect/kafka-connect.log");

    postgreSql =
        new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:11").asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withInitScript("postgres-setup.sql")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USERNAME)
            .withPassword(DB_PASSWORD)
            .withStartupTimeout(Duration.of(5, MINUTES));

    // Start containers (backend already started)
    Startables.deepStart(Stream.of(zookeeper, kafka, kafkaConnect, postgreSql)).join();

    // Wait until Kafka Connect container is ready
    given()
        .contentType(ContentType.JSON)
        .when()
        .get(getKafkaConnectUrl())
        .then()
        .statusCode(HttpStatus.SC_OK);
  }

  @BeforeEach
  public void reset() {
    deleteConnectorIfExists();
    clearPostgresTable();
    // Clear spans from backend (like smoke tests)
    clearBackendTraces();
  }

  private static void clearBackendTraces() {
    try {
      String backendUrl = getBackendUrl();
      given().when().get(backendUrl + "/clear").then().statusCode(200);
    } catch (RuntimeException e) {
      // Ignore failures to clear traces
    }
  }

  @Test
  public void testKafkaConnectPostgresSinkTaskInstrumentation()
      throws IOException, InterruptedException {
    // Create unique topic name
    String uniqueTopicName = TOPIC_NAME + "-" + System.currentTimeMillis();

    // Setup Kafka Connect JDBC Sink connector
    setupPostgresSinkConnector(uniqueTopicName);

    // Create topic and wait for availability
    createTopic(uniqueTopicName);
    awaitForTopicCreation(uniqueTopicName);

    // Produce a test message
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      producer.send(
          new ProducerRecord<>(
              uniqueTopicName,
              "test-key",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":1,\"name\":\"TestUser\"}}"));
      producer.flush();
    }

    // Wait for message processing (increased timeout for ARM64 Docker emulation)
    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromPostgres() >= 1);

    // Wait for spans to arrive at backend (increased timeout for ARM64 Docker emulation)
    String backendUrl = getBackendUrl();
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              try {
                String traces =
                    given()
                        .when()
                        .get(backendUrl + "/get-traces")
                        .then()
                        .statusCode(200)
                        .extract()
                        .asString();

                return !traces.equals("[]");
              } catch (RuntimeException e) {
                return false;
              }
            });

    // Retrieve and verify spans using clean protobuf approach
    String tracesJson =
        given().when().get(backendUrl + "/get-traces").then().statusCode(200).extract().asString();

    // Extract spans and links using separate deserialization method
    TracingData tracingData;
    try {
      tracingData = deserializeAndExtractSpans(tracesJson, uniqueTopicName);
    } catch (Exception e) {
      logger.error("Failed to deserialize and extract spans: {}", e.getMessage(), e);
      throw new AssertionError("Span deserialization failed", e);
    }

    // Perform distributed tracing assertions
    // Assertion 1: Verify all spans and links are not null
    assertThat(tracingData.kafkaConnectConsumerSpan)
        .as("Kafka Connect Consumer span should be found for topic: %s", uniqueTopicName)
        .isNotNull();
    assertThat(tracingData.databaseSpan).as("Database span should be found").isNotNull();
    assertThat(tracingData.extractedSpanLink)
        .as("Kafka Connect Consumer span should have span links")
        .isNotNull();
    assertThat(tracingData.extractedSpanLink.linkedTraceId)
        .as("Span link should have a valid linked trace ID")
        .isNotEmpty();
    assertThat(tracingData.extractedSpanLink.linkedSpanId)
        .as("Span link should have a valid linked span ID")
        .isNotEmpty();

    // Assertion 2: Check if link traceId and Kafka Connect trace ID are same or not
    assertThat(tracingData.extractedSpanLink.linkedTraceId)
        .as(
            "Span link trace ID should match Kafka Connect Consumer trace ID (distributed trace continuity)")
        .isEqualTo(tracingData.kafkaConnectConsumerSpan.traceId);

    // Assertion 3: Check span kind is consumer
    assertThat(tracingData.kafkaConnectConsumerSpan.kind)
        .as("Kafka Connect span should have CONSUMER span kind")
        .isEqualTo("SPAN_KIND_CONSUMER");

    // Assertion 4: Check if Kafka Connect traceId and database span traceId are similar
    assertThat(tracingData.kafkaConnectConsumerSpan.traceId)
        .as("Kafka Connect Consumer and Database spans should share the same trace ID")
        .isEqualTo(tracingData.databaseSpan.traceId);
    assertThat(tracingData.databaseSpan.parentSpanId)
        .as("Database span must have a parent span ID")
        .isNotNull()
        .as("Database span parent should be Kafka Connect Consumer span")
        .isEqualTo(tracingData.kafkaConnectConsumerSpan.spanId);
  }

  @AfterAll
  public static void cleanup() {
    // Close AdminClient first to release Kafka connections
    if (adminClient != null) {
      try {
        adminClient.close();
      } catch (RuntimeException e) {
        logger.error("Error closing AdminClient: " + e.getMessage());
      }
    }

    // Stop all containers in reverse order of startup to ensure clean shutdown
    if (kafkaConnect != null) {
      try {
        kafkaConnect.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping Kafka Connect: " + e.getMessage());
      }
    }

    if (postgreSql != null) {
      try {
        postgreSql.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping PostgreSQL: " + e.getMessage());
      }
    }

    if (kafka != null) {
      try {
        kafka.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping Kafka: " + e.getMessage());
      }
    }

    if (zookeeper != null) {
      try {
        zookeeper.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping Zookeeper: " + e.getMessage());
      }
    }

    if (backend != null) {
      try {
        backend.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping backend: " + e.getMessage());
      }
    }

    if (network != null) {
      try {
        network.close();
      } catch (RuntimeException e) {
        logger.error("Error closing network: " + e.getMessage());
      }
    }
  }

  // Private methods
  private static void setupPostgresSinkConnector(String topicName) throws IOException {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("connector.class", "io.confluent.connect.jdbc.JdbcSinkConnector");
    configMap.put("tasks.max", "1");
    configMap.put(
        "connection.url",
        format(
            Locale.ROOT,
            "jdbc:postgresql://%s:5432/%s?loggerLevel=OFF",
            POSTGRES_NETWORK_ALIAS,
            DB_NAME));
    configMap.put("connection.user", DB_USERNAME);
    configMap.put("connection.password", DB_PASSWORD);
    configMap.put("topics", topicName);
    configMap.put("auto.create", "false");
    configMap.put("auto.evolve", "false");
    configMap.put("insert.mode", "insert");
    configMap.put("delete.enabled", "false");
    configMap.put("key.converter", "org.apache.kafka.connect.storage.StringConverter");
    configMap.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
    configMap.put("value.converter.schemas.enable", "true");
    configMap.put("table.name.format", DB_TABLE_PERSON);
    configMap.put("pk.mode", "none");

    String payload =
        MAPPER.writeValueAsString(ImmutableMap.of("name", CONNECTOR_NAME, "config", configMap));
    given()
        .log()
        .headers()
        .contentType(ContentType.JSON)
        .accept(ContentType.JSON)
        .body(payload)
        .when()
        .post(getKafkaConnectUrl() + "/connectors")
        .andReturn()
        .then()
        .log()
        .all();
  }

  private static void createTopic(String topicName) {
    try (AdminClient adminClient = createAdminClient()) {
      NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        throw new VerifyException("Failed to create topic: " + topicName, e);
      } else if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
        // Topic already exists, continue
      } else {
        logger.error("Error creating topic: {}", e.getMessage());
        throw new VerifyException("Failed to create topic: " + topicName, e);
      }
    }
  }

  private static void awaitForTopicCreation(String topicName) {
    try (AdminClient adminClient = createAdminClient()) {
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .until(() -> adminClient.listTopics().names().get().contains(topicName));
    }
  }

  private static AdminClient createAdminClient() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    return KafkaAdminClient.create(properties);
  }

  private static long getRecordCountFromPostgres() {
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + DB_TABLE_PERSON)) {
      if (rs.next()) {
        return rs.getLong(1);
      }
    } catch (SQLException e) {
      logger.warn("Failed to count PostgreSQL records: {}", e.getMessage());
      return 0;
    }
    return 0;
  }

  private static void clearPostgresTable() {
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement()) {
      st.executeUpdate("DELETE FROM " + DB_TABLE_PERSON);
      logger.info("Cleared PostgreSQL table: {}", DB_TABLE_PERSON);
    } catch (SQLException e) {
      logger.warn("Failed to clear PostgreSQL table: {}", e.getMessage());
    }
  }

  private static void deleteConnectorIfExists() {
    given()
        .log()
        .headers()
        .contentType(ContentType.JSON)
        .when()
        .delete(getKafkaConnectUrl() + "/connectors/" + CONNECTOR_NAME)
        .andReturn()
        .then()
        .log()
        .all();
  }

  /** Deserialize traces JSON and extract span information and links */
  private static TracingData deserializeAndExtractSpans(String tracesJson, String expectedTopicName)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(tracesJson);

    assertThat(rootNode.isArray()).as("Traces JSON should be an array").isTrue();

    // Extract all spans and organize by type
    SpanInfo kafkaConnectConsumerSpan = null;
    SpanInfo databaseSpan = null;
    SpanLinkInfo extractedSpanLink = null;

    // Process each trace request in the JSON array
    for (JsonNode traceRequestNode : rootNode) {
      JsonNode resourceSpansArray = traceRequestNode.get("resourceSpans");
      if (resourceSpansArray == null || !resourceSpansArray.isArray()) {
        continue;
      }

      for (JsonNode resourceSpansNode : resourceSpansArray) {
        JsonNode scopeSpansArray = resourceSpansNode.get("scopeSpans");
        if (scopeSpansArray == null || !scopeSpansArray.isArray()) {
          continue;
        }

        for (JsonNode scopeSpansNode : scopeSpansArray) {
          JsonNode scopeNode = scopeSpansNode.get("scope");
          String scopeName =
              scopeNode != null && scopeNode.get("name") != null
                  ? scopeNode.get("name").asText()
                  : "";

          JsonNode spansArray = scopeSpansNode.get("spans");
          if (spansArray == null || !spansArray.isArray()) {
            continue;
          }

          for (JsonNode spanNode : spansArray) {
            String spanName = spanNode.get("name") != null ? spanNode.get("name").asText() : "";
            String traceId =
                spanNode.get("traceId") != null ? spanNode.get("traceId").asText() : "";
            String spanId = spanNode.get("spanId") != null ? spanNode.get("spanId").asText() : "";
            String parentSpanId =
                spanNode.get("parentSpanId") != null
                        && !spanNode.get("parentSpanId").asText().isEmpty()
                    ? spanNode.get("parentSpanId").asText()
                    : null;
            String spanKind = spanNode.get("kind") != null ? spanNode.get("kind").asText() : "";

            // Identify spans in our end-to-end flow
            if (scopeName.contains("kafka-connect")
                && spanName.contains(expectedTopicName)
                && spanKind.equals("SPAN_KIND_CONSUMER")) {
              kafkaConnectConsumerSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind);

              // Extract span link information for verification
              JsonNode linksArray = spanNode.get("links");
              if (linksArray != null && linksArray.isArray() && linksArray.size() > 0) {
                JsonNode firstLink = linksArray.get(0);
                String linkedTraceId =
                    firstLink.get("traceId") != null ? firstLink.get("traceId").asText() : "";
                String linkedSpanId =
                    firstLink.get("spanId") != null ? firstLink.get("spanId").asText() : "";

                extractedSpanLink = new SpanLinkInfo(linkedTraceId, linkedSpanId);
              }
            } else if (scopeName.contains("jdbc")
                && (spanName.contains("INSERT")
                    || spanName.contains("UPDATE")
                    || spanName.contains("DELETE")
                    || spanName.contains("SELECT")
                    || spanName.contains(DB_TABLE_PERSON))) {
              databaseSpan = new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind);
            }
          }
        }
      }
    }

    return new TracingData(kafkaConnectConsumerSpan, databaseSpan, extractedSpanLink);
  }

  // Helper class to hold all tracing data
  private static class TracingData {
    final SpanInfo kafkaConnectConsumerSpan;
    final SpanInfo databaseSpan;
    final SpanLinkInfo extractedSpanLink;

    TracingData(
        SpanInfo kafkaConnectConsumerSpan, SpanInfo databaseSpan, SpanLinkInfo extractedSpanLink) {
      this.kafkaConnectConsumerSpan = kafkaConnectConsumerSpan;
      this.databaseSpan = databaseSpan;
      this.extractedSpanLink = extractedSpanLink;
    }
  }

  // Helper class to hold span information
  private static class SpanInfo {
    final String traceId;
    final String spanId;
    final String parentSpanId;
    final String kind;

    SpanInfo(String name, String traceId, String spanId, String parentSpanId, String kind) {
      this.traceId = traceId;
      this.spanId = spanId;
      this.parentSpanId = parentSpanId;
      this.kind = kind;
    }
  }

  // Helper class to hold span link information
  private static class SpanLinkInfo {
    final String linkedTraceId;
    final String linkedSpanId;

    SpanLinkInfo(String linkedTraceId, String linkedSpanId) {
      this.linkedTraceId = linkedTraceId;
      this.linkedSpanId = linkedSpanId;
    }
  }
}
