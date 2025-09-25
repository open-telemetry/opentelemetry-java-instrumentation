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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.restassured.http.ContentType;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
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
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
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
@SuppressWarnings({"rawtypes", "unchecked", "deprecation", "unused"})
class MongoKafkaConnectSinkTaskTest {

  private static final Logger logger = LoggerFactory.getLogger(MongoKafkaConnectSinkTaskTest.class);

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
  private static final String MONGO_NETWORK_ALIAS = "mongodb";
  private static final String BACKEND_ALIAS = "backend";
  private static final int BACKEND_PORT = 8080;

  // Database
  private static final String DB_NAME = "testdb";
  private static final String COLLECTION_NAME = "person";

  // Other constants
  private static final String PLUGIN_PATH_CONTAINER = "/usr/share/java";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final String CONNECTOR_NAME = "test-mongo-connector";
  private static final String TOPIC_NAME = "test-mongo-topic";
  private static final String KAFKA_CONNECT_SCOPE = "kafka-connect";

  // Docker network / containers
  private static Network network;
  private static FixedHostPortGenericContainer<?> kafka;
  private static GenericContainer<?> zookeeper;
  private static GenericContainer<?> kafkaConnect;
  private static MongoDBContainer mongoDB;
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

  private static String getInternalKafkaBoostrapServers() {
    return KAFKA_NETWORK_ALIAS + ":" + KAFKA_INTERNAL_ADVERTISED_LISTENERS_PORT;
  }

  private static String getKafkaBoostrapServers() {
    return kafka.getHost() + ":" + kafkaExposedPort;
  }

  @BeforeAll
  public static void setup() throws IOException {

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
    File agentFile = new File(agentPath);

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
                    + "mongodb/kafka-connect-mongodb:1.11.0 && "
                    + "echo 'Starting Kafka Connect with logging to /var/log/kafka-connect/' && "
                    + "/etc/confluent/docker/run 2>&1 | tee /var/log/kafka-connect/kafka-connect.log");

    mongoDB =
        new MongoDBContainer(DockerImageName.parse("mongo:4.4"))
            .withNetwork(network)
            .withNetworkAliases(MONGO_NETWORK_ALIAS)
            .withStartupTimeout(Duration.of(5, MINUTES));

    // Start containers (backend already started)
    Startables.deepStart(Stream.of(zookeeper, kafka, kafkaConnect, mongoDB)).join();

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
    clearMongoCollection();
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

  private static String getBackendUrl() {
    return format(
        Locale.ROOT, "http://%s:%d", backend.getHost(), backend.getMappedPort(BACKEND_PORT));
  }

  @Test
  public void testKafkaConnectMongoSinkTaskInstrumentation()
      throws IOException, InterruptedException {
    // Use base topic name for consistent span naming
    String uniqueTopicName = TOPIC_NAME;

    // Setup Kafka Connect MongoDB Sink connector
    setupMongoSinkConnector(uniqueTopicName);

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
          new ProducerRecord<>(uniqueTopicName, "test-key", "{\"id\":1,\"name\":\"TestUser\"}"));
      producer.flush();
    }

    // Wait for message processing (increased timeout for ARM64 Docker emulation)
    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 1);

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

    // Assertion 2: Check if link traceId and Kafka Connect trace ID are same
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

  @Test
  public void testKafkaConnectMongoSinkTaskMultiTopicInstrumentation()
      throws IOException, InterruptedException {
    // Create multiple topic names for consistent span naming
    String topicName1 = TOPIC_NAME + "-1";
    String topicName2 = TOPIC_NAME + "-2";
    String topicName3 = TOPIC_NAME + "-3";
    
    // Setup Kafka Connect MongoDB Sink connector with multiple topics
    setupMongoSinkConnectorMultiTopic(topicName1, topicName2, topicName3);

    // Create all topics and wait for availability
    createTopic(topicName1);
    createTopic(topicName2);
    createTopic(topicName3);
    awaitForTopicCreation(topicName1);
    awaitForTopicCreation(topicName2);
    awaitForTopicCreation(topicName3);

    // Produce test messages to different topics
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      // Send messages to different topics
      producer.send(new ProducerRecord<>(topicName1, "key1", "{\"id\":1,\"name\":\"User1\",\"source\":\"topic1\"}"));
      producer.send(new ProducerRecord<>(topicName2, "key2", "{\"id\":2,\"name\":\"User2\",\"source\":\"topic2\"}"));
      producer.send(new ProducerRecord<>(topicName3, "key3", "{\"id\":3,\"name\":\"User3\",\"source\":\"topic3\"}"));
      producer.flush();
    }

    // Wait for message processing (increased timeout for ARM64 Docker emulation)
    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 3);

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

    // Write resourceSpans to desktop file for inspection
    try {
      java.nio.file.Path desktopPath = java.nio.file.Paths.get(System.getProperty("user.home"), "Desktop", "kafka-connect-multi-topic-spans.json");
      java.nio.file.Files.write(desktopPath, tracesJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      System.out.println("✅ Wrote resourceSpans to: " + desktopPath.toString());
    } catch (Exception e) {
      System.err.println("❌ Failed to write spans to desktop: " + e.getMessage());
    }

    // Extract spans and verify multi-topic span naming
    MultiTopicTracingData tracingData;
    try {
      tracingData = deserializeAndExtractMultiTopicSpans(tracesJson, topicName1, topicName2, topicName3);
    } catch (Exception e) {
      logger.error("Failed to deserialize and extract spans: {}", e.getMessage(), e);
      throw new AssertionError("Span deserialization failed", e);
    }

    // Perform multi-topic distributed tracing assertions
    // Assertion 1: Verify Kafka Connect Consumer span exists and has correct multi-topic naming
    assertThat(tracingData.kafkaConnectConsumerSpan)
        .as("Kafka Connect Consumer span should be found for multi-topic processing")
        .isNotNull();
    
    // Assertion 2: Verify span name contains all topics in bracket format (order may vary)
    assertThat(tracingData.kafkaConnectConsumerSpan.name)
        .as("Span name should contain all topics in bracket format")
        .contains(topicName1)
        .contains(topicName2)
        .contains(topicName3)
        .startsWith("[")
        .endsWith("] process");

    // Assertion 3: Verify database span exists
    assertThat(tracingData.databaseSpan).as("Database span should be found").isNotNull();

    // Assertion 4: Verify span links exist
    assertThat(tracingData.extractedSpanLink)
        .as("Kafka Connect Consumer span should have span links")
        .isNotNull();

    // Assertion 5: Check span kind is consumer
    assertThat(tracingData.kafkaConnectConsumerSpan.kind)
        .as("Kafka Connect span should have CONSUMER span kind")
        .isEqualTo("SPAN_KIND_CONSUMER");

    // Assertion 6: Check if Kafka Connect traceId and database span traceId are similar
    assertThat(tracingData.kafkaConnectConsumerSpan.traceId)
        .as("Kafka Connect Consumer and Database spans should share the same trace ID")
        .isEqualTo(tracingData.databaseSpan.traceId);
    assertThat(tracingData.databaseSpan.parentSpanId)
        .as("Database span must have a parent span ID")
        .isNotNull()
        .as("Database span parent should be Kafka Connect Consumer span")
        .isEqualTo(tracingData.kafkaConnectConsumerSpan.spanId);
  }

  // Private methods
  private static void setupMongoSinkConnector(String topicName) throws IOException {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("connector.class", "com.mongodb.kafka.connect.MongoSinkConnector");
    configMap.put("tasks.max", "1");
    configMap.put("connection.uri", format(Locale.ROOT, "mongodb://%s:27017", MONGO_NETWORK_ALIAS));
    configMap.put("database", DB_NAME);
    configMap.put("collection", COLLECTION_NAME);
    configMap.put("topics", topicName);
    configMap.put("key.converter", "org.apache.kafka.connect.storage.StringConverter");
    configMap.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
    configMap.put("value.converter.schemas.enable", "false");
    configMap.put(
        "document.id.strategy",
        "com.mongodb.kafka.connect.sink.processor.id.strategy.BsonOidStrategy");

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

  private static void setupMongoSinkConnectorMultiTopic(String... topicNames) throws IOException {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("connector.class", "com.mongodb.kafka.connect.MongoSinkConnector");
    configMap.put("tasks.max", "1");
    configMap.put("connection.uri", format(Locale.ROOT, "mongodb://%s:27017", MONGO_NETWORK_ALIAS));
    configMap.put("database", DB_NAME);
    configMap.put("collection", COLLECTION_NAME);
    // Configure multiple topics separated by commas
    configMap.put("topics", String.join(",", topicNames));
    configMap.put("key.converter", "org.apache.kafka.connect.storage.StringConverter");
    configMap.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
    configMap.put("value.converter.schemas.enable", "false");
    configMap.put(
        "document.id.strategy",
        "com.mongodb.kafka.connect.sink.processor.id.strategy.BsonOidStrategy");

    String payload =
        MAPPER.writeValueAsString(ImmutableMap.of("name", CONNECTOR_NAME + "-multi", "config", configMap));
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

  private static long getRecordCountFromMongo() {
    try (MongoClient mongoClient = MongoClients.create(mongoDB.getConnectionString())) {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      return collection.countDocuments();
    } catch (RuntimeException e) {
      return 0;
    }
  }

  private static void clearMongoCollection() {
    try (MongoClient mongoClient = MongoClients.create(mongoDB.getConnectionString())) {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      collection.drop();
    } catch (RuntimeException e) {
      // Ignore cleanup failures
    }
  }

  private static int getRandomFreePort() {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      return 0;
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

    if (mongoDB != null) {
      try {
        mongoDB.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping MongoDB: " + e.getMessage());
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

  /** Deserialize traces JSON and extract span information and links */
  private static TracingData deserializeAndExtractSpans(String tracesJson, String expectedTopicName)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(tracesJson);

    assertThat(rootNode.isArray()).as("Traces JSON should be an array").isTrue();

    // Extract all spans and organize by type
    SpanInfo kafkaProducerSpan = null;
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
            if (scopeName.contains("kafka-clients")
                && spanName.contains(expectedTopicName)
                && spanKind.equals("SPAN_KIND_PRODUCER")) {
              kafkaProducerSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);
            } else if (scopeName.contains(KAFKA_CONNECT_SCOPE)
                && spanName.contains(expectedTopicName)
                && spanKind.equals("SPAN_KIND_CONSUMER")) {
              kafkaConnectConsumerSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);

              // Extract span link information for verification
              JsonNode linksArray = spanNode.get("links");
              if (linksArray != null && linksArray.isArray() && linksArray.size() > 0) {
                JsonNode firstLink = linksArray.get(0);
                String linkedTraceId =
                    firstLink.get("traceId") != null ? firstLink.get("traceId").asText() : "";
                String linkedSpanId =
                    firstLink.get("spanId") != null ? firstLink.get("spanId").asText() : "";
                int flags = firstLink.get("flags") != null ? firstLink.get("flags").asInt() : 0;

                extractedSpanLink = new SpanLinkInfo(linkedTraceId, linkedSpanId, flags);
              }
            } else if (scopeName.contains("mongo") && spanName.contains("testdb.person")) {
              databaseSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);
            }
          }
        }
      }
    }

    return new TracingData(
        kafkaProducerSpan, kafkaConnectConsumerSpan, databaseSpan, extractedSpanLink);
  }

  /** Deserialize traces JSON and extract span information for multi-topic scenarios */
  private static MultiTopicTracingData deserializeAndExtractMultiTopicSpans(String tracesJson, String... expectedTopicNames)
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

            // Identify spans in our multi-topic flow
            if (scopeName.contains(KAFKA_CONNECT_SCOPE) && spanKind.equals("SPAN_KIND_CONSUMER")) {
              // Check if span name contains any of the expected topics or the multi-topic format
              boolean containsExpectedTopics = false;
              for (String topicName : expectedTopicNames) {
                if (spanName.contains(topicName)) {
                  containsExpectedTopics = true;
                  break;
                }
              }
              
              if (containsExpectedTopics) {
                kafkaConnectConsumerSpan =
                    new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);

                // Extract span link information for verification
                JsonNode linksArray = spanNode.get("links");
                if (linksArray != null && linksArray.isArray() && linksArray.size() > 0) {
                  JsonNode firstLink = linksArray.get(0);
                  String linkedTraceId =
                      firstLink.get("traceId") != null ? firstLink.get("traceId").asText() : "";
                  String linkedSpanId =
                      firstLink.get("spanId") != null ? firstLink.get("spanId").asText() : "";
                  int flags = firstLink.get("flags") != null ? firstLink.get("flags").asInt() : 0;

                  extractedSpanLink = new SpanLinkInfo(linkedTraceId, linkedSpanId, flags);
                }
              }
            } else if (scopeName.contains("mongo") && spanName.contains("testdb.person")) {
              databaseSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);
            }
          }
        }
      }
    }

    return new MultiTopicTracingData(kafkaConnectConsumerSpan, databaseSpan, extractedSpanLink);
  }

  /** Helper class to hold all extracted multi-topic tracing data */
  private static class MultiTopicTracingData {
    final SpanInfo kafkaConnectConsumerSpan;
    final SpanInfo databaseSpan;
    final SpanLinkInfo extractedSpanLink;

    MultiTopicTracingData(
        SpanInfo kafkaConnectConsumerSpan,
        SpanInfo databaseSpan,
        SpanLinkInfo extractedSpanLink) {
      this.kafkaConnectConsumerSpan = kafkaConnectConsumerSpan;
      this.databaseSpan = databaseSpan;
      this.extractedSpanLink = extractedSpanLink;
    }
  }

  /** Helper class to hold all extracted tracing data */
  private static class TracingData {
    final SpanInfo kafkaProducerSpan;
    final SpanInfo kafkaConnectConsumerSpan;
    final SpanInfo databaseSpan;
    final SpanLinkInfo extractedSpanLink;

    TracingData(
        SpanInfo kafkaProducerSpan,
        SpanInfo kafkaConnectConsumerSpan,
        SpanInfo databaseSpan,
        SpanLinkInfo extractedSpanLink) {
      this.kafkaProducerSpan = kafkaProducerSpan;
      this.kafkaConnectConsumerSpan = kafkaConnectConsumerSpan;
      this.databaseSpan = databaseSpan;
      this.extractedSpanLink = extractedSpanLink;
    }
  }

  /** Helper class to hold span link information */
  private static class SpanLinkInfo {
    final String linkedTraceId;
    final String linkedSpanId;
    final int flags;

    SpanLinkInfo(String linkedTraceId, String linkedSpanId, int flags) {
      this.linkedTraceId = linkedTraceId;
      this.linkedSpanId = linkedSpanId;
      this.flags = flags;
    }
  }

  // Helper class to hold span information
  private static class SpanInfo {
    final String name;
    final String traceId;
    final String spanId;
    final String parentSpanId;
    final String kind;
    final String scope;

    SpanInfo(
        String name,
        String traceId,
        String spanId,
        String parentSpanId,
        String kind,
        String scope) {
      this.name = name;
      this.traceId = traceId;
      this.spanId = spanId;
      this.parentSpanId = parentSpanId;
      this.kind = kind;
      this.scope = scope;
    }
  }
}
