/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.MINUTES;
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
import java.util.Date;
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

    // Create log directory on desktop
    File logDir = new File(System.getProperty("user.home") + "/Desktop/kafka-connect-logs");
    if (!logDir.exists()) {
      logDir.mkdirs();
      logger.info("Created log directory: {}", logDir.getAbsolutePath());
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

    logger.info("Starting backend container...");
    backend.start();
    logger.info(
        "Backend container started at: http://{}:{}",
        backend.getHost(),
        backend.getMappedPort(BACKEND_PORT));

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
    logger.info("=== AGENT JAR PATH: {} ===", agentPath);
    File agentFile = new File(agentPath);
    logger.info("=== AGENT JAR EXISTS: {} ===", agentFile.exists());
    logger.info("=== AGENT JAR SIZE: {} bytes ===", agentFile.length());
    logger.info("=== AGENT JAR LAST MODIFIED: {} ===", new Date(agentFile.lastModified()));

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
    // Create unique topic name
    String uniqueTopicName = TOPIC_NAME + "-" + System.currentTimeMillis();

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

    // Write traces JSON to file for inspection
    writeTracesToFile(tracesJson, "mongo-traces.json");

    // Use structured deserialization approach with JsonFormat and add distributed tracing
    // assertions
    try {
      verifyDistributedTracingFlow(tracesJson, uniqueTopicName);
    } catch (Exception e) {
      logger.error("Failed to verify distributed tracing flow: {}", e.getMessage(), e);
      throw new AssertionError("Distributed tracing verification failed", e);
    }
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

  private static void createTopic(String topicName) {
    try (AdminClient adminClient = createAdminClient()) {
      NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
      logger.info("Created topic: {}", topicName);
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
        throw new VerifyException("Failed to create topic: " + topicName, e);
      } else if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
        logger.info("Topic already exists: {}", topicName);
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
      logger.warn("Failed to count MongoDB documents: {}", e.getMessage());
      return 0;
    }
  }

  private static void clearMongoCollection() {
    try (MongoClient mongoClient = MongoClients.create(mongoDB.getConnectionString())) {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      collection.drop();
      logger.info("Cleared MongoDB collection: {}", COLLECTION_NAME);
    } catch (RuntimeException e) {
      logger.warn("Failed to clear MongoDB collection: {}", e.getMessage());
    }
  }

  private static int getRandomFreePort() {
    try (ServerSocket serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      logger.error("Failed to get random free port", e);
      return 0;
    }
  }

  private static void deleteConnectorIfExists() {
    logger.info("Deleting connector [ {} ] if exists", CONNECTOR_NAME);
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
        logger.info("AdminClient closed");
      } catch (RuntimeException e) {
        logger.error("Error closing AdminClient: " + e.getMessage());
      }
    }

    // Stop all containers in reverse order of startup to ensure clean shutdown
    if (kafkaConnect != null) {
      try {
        kafkaConnect.stop();
        logger.info("Kafka Connect container stopped");
      } catch (RuntimeException e) {
        logger.error("Error stopping Kafka Connect: " + e.getMessage());
      }
    }

    if (mongoDB != null) {
      try {
        mongoDB.stop();
        logger.info("MongoDB container stopped");
      } catch (RuntimeException e) {
        logger.error("Error stopping MongoDB: " + e.getMessage());
      }
    }

    if (kafka != null) {
      try {
        kafka.stop();
        logger.info("Kafka container stopped");
      } catch (RuntimeException e) {
        logger.error("Error stopping Kafka: " + e.getMessage());
      }
    }

    if (zookeeper != null) {
      try {
        zookeeper.stop();
        logger.info("Zookeeper container stopped");
      } catch (RuntimeException e) {
        logger.error("Error stopping Zookeeper: " + e.getMessage());
      }
    }

    if (backend != null) {
      try {
        backend.stop();
        logger.info("Backend container stopped");
      } catch (RuntimeException e) {
        logger.error("Error stopping backend: " + e.getMessage());
      }
    }

    if (network != null) {
      try {
        network.close();
        logger.info("Network closed");
      } catch (RuntimeException e) {
        logger.error("Error closing network: " + e.getMessage());
      }
    }

    logger.info("Test cleanup complete");
  }

  /** Writes traces JSON to a file for inspection and debugging. */
  private static void writeTracesToFile(String tracesJson, String filename) {
    try {
      File outputDir = new File(System.getProperty("user.home") + "/Desktop/kafka-connect-logs");
      if (!outputDir.exists()) {
        outputDir.mkdirs();
      }

      File tracesFile = new File(outputDir, filename);

      // Pretty print the JSON for better readability
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
      JsonNode jsonNode = objectMapper.readTree(tracesJson);
      String prettyJson = objectMapper.writeValueAsString(jsonNode);

      try (java.io.Writer writer =
          java.nio.file.Files.newBufferedWriter(
              tracesFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
        writer.write(prettyJson);
      }

      logger.info("📄 Traces JSON written to: {}", tracesFile.getAbsolutePath());
    } catch (Exception e) {
      logger.warn("Failed to write traces to file: {}", e.getMessage());
    }
  }

  /**
   * Simple approach - just parse JSON and print the trace info you want to see! Since the JSON is
   * already in resourceSpans format, we can extract what we need directly
   */
  private static void deserializeOtlpDataFromJson(String tracesJson, String expectedTopicName)
      throws Exception {
    logger.info("🔍 Parsing JSON traces to extract span information");

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode tracesArray = objectMapper.readTree(tracesJson);

    // Process each trace in the JSON array
    for (JsonNode traceNode : tracesArray) {
      JsonNode resourceSpansArray = traceNode.get("resourceSpans");
      if (resourceSpansArray != null && resourceSpansArray.isArray()) {

        // Process each ResourceSpans
        for (JsonNode resourceSpansNode : resourceSpansArray) {
          logger.info("Processing Resource with attributes:");

          JsonNode resourceNode = resourceSpansNode.get("resource");
          if (resourceNode != null) {
            JsonNode attributes = resourceNode.get("attributes");
            if (attributes != null && attributes.isArray()) {
              for (JsonNode attr : attributes) {
                String key = attr.path("key").asText();
                String value = extractAttributeValue(attr.path("value"));
                logger.info("  " + key + ": " + value);
              }
            }
          }

          JsonNode scopeSpansArray = resourceSpansNode.get("scopeSpans");
          if (scopeSpansArray != null && scopeSpansArray.isArray()) {

            // Process each ScopeSpans
            for (JsonNode scopeSpansNode : scopeSpansArray) {
              JsonNode scopeNode = scopeSpansNode.get("scope");
              String scopeName = scopeNode != null ? scopeNode.path("name").asText() : "unknown";
              logger.info("Scope name: {}", scopeName);

              JsonNode spansArray = scopeSpansNode.get("spans");
              if (spansArray != null && spansArray.isArray()) {

                // Process each Span
                for (JsonNode spanNode : spansArray) {
                  String spanName = spanNode.path("name").asText();
                  String spanId = spanNode.path("spanId").asText();
                  String traceId = spanNode.path("traceId").asText();
                  String kind = spanNode.path("kind").asText();

                  logger.info("  Span: " + spanName);
                  logger.info("    Span ID: " + spanId);
                  logger.info("    Trace ID: " + traceId);
                  logger.info("    Kind: " + kind);

                  // Print span attributes
                  JsonNode spanAttributes = spanNode.path("attributes");
                  if (spanAttributes.isArray()) {
                    for (JsonNode attr : spanAttributes) {
                      String key = attr.path("key").asText();
                      String value = extractAttributeValue(attr.path("value"));
                      logger.info("      Attribute: " + key + " -> " + value);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  /** Helper to extract attribute values from different types */
  private static String extractAttributeValue(JsonNode valueNode) {
    if (valueNode.has("stringValue")) {
      return valueNode.path("stringValue").asText();
    } else if (valueNode.has("intValue")) {
      return String.valueOf(valueNode.path("intValue").asLong());
    } else if (valueNode.has("boolValue")) {
      return String.valueOf(valueNode.path("boolValue").asBoolean());
    } else if (valueNode.has("arrayValue")) {
      return "[array]"; // Simplified for readability
    }
    return valueNode.toString();
  }

  /**
   * Your exact deserializeOtlpData method using TracesData.parseFrom(otlpBytes)! Just prints trace
   * info, no assertions
   */
  private static void verifyDistributedTracingFlow(String tracesJson, String expectedTopicName)
      throws IOException {
    logger.info(
        "🔍 Verifying Kafka Connect distributed tracing flow for topic: {}", expectedTopicName);

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(tracesJson);

    if (!rootNode.isArray()) {
      throw new AssertionError("The input JSON is not an array.");
    }

    logger.info("🔍 Successfully parsed {} trace requests.", rootNode.size());

    // Extract all spans and organize by type
    SpanInfo kafkaConnectConsumerSpan = null;
    SpanInfo databaseSpan = null;
    boolean hasSpanLinks = false;

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

            // Identify spans in our Kafka Connect flow
            if (scopeName.contains("kafka-connect")
                && spanName.contains(expectedTopicName)
                && spanKind.equals("SPAN_KIND_CONSUMER")) {
              kafkaConnectConsumerSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);
              logger.info("✅ Found Kafka Connect Consumer Span: {} ({})", spanName, spanId);

              // Check for span links (indicating producer -> consumer linking)
              JsonNode linksArray = spanNode.get("links");
              if (linksArray != null && linksArray.isArray() && linksArray.size() > 0) {
                hasSpanLinks = true;
                logger.info("✅ Kafka Connect span has {} span links", linksArray.size());
                for (JsonNode linkNode : linksArray) {
                  String linkedTraceId =
                      linkNode.get("traceId") != null ? linkNode.get("traceId").asText() : "";
                  String linkedSpanId =
                      linkNode.get("spanId") != null ? linkNode.get("spanId").asText() : "";
                  logger.info("  🔗 Linked to: Trace {} Span {}", linkedTraceId, linkedSpanId);
                }
              }
            } else if (scopeName.contains("mongo") && spanName.contains("testdb.person")) {
              databaseSpan =
                  new SpanInfo(spanName, traceId, spanId, parentSpanId, spanKind, scopeName);
              logger.info("✅ Found Database Span: {} ({})", spanName, spanId);
            }
          }
        }
      }
    }

    // Perform the critical Kafka Connect distributed tracing assertions
    logger.info("🧪 Performing Kafka Connect distributed tracing assertions...");

    // Check that we found the required spans
    if (kafkaConnectConsumerSpan == null) {
      throw new AssertionError(
          "❌ Kafka Connect Consumer span not found for topic: " + expectedTopicName);
    }
    if (databaseSpan == null) {
      throw new AssertionError("❌ Database span not found");
    }

    // Assertion 1: Same Trace ID between Kafka Connect Consumer and Database spans
    if (!kafkaConnectConsumerSpan.traceId.equals(databaseSpan.traceId)) {
      throw new AssertionError(
          "❌ Trace ID mismatch between Kafka Connect Consumer ("
              + kafkaConnectConsumerSpan.traceId
              + ") and Database ("
              + databaseSpan.traceId
              + ")");
    }
    logger.info(
        "✅ ASSERTION 1 PASSED: Kafka Connect and Database spans share the same trace ID: {}",
        kafkaConnectConsumerSpan.traceId);

    // Assertion 2: Span linking - Kafka Connect span should have span links if trace context was
    // propagated
    if (!hasSpanLinks) {
      logger.info(
          "ℹ️  ASSERTION 2 INFO: Kafka Connect span has no span links (expected since test JVM producer is not instrumented)");
    } else {
      logger.info(
          "✅ ASSERTION 2 PASSED: Kafka Connect span has span links, indicating proper trace context propagation and KafkaConnectBatchProcessSpanLinksExtractor is working");
    }

    // Assertion 3: Parent-child relationship between Kafka Connect and Database
    if (databaseSpan.parentSpanId == null) {
      throw new AssertionError("❌ Database span has no parent span ID");
    }
    if (!databaseSpan.parentSpanId.equals(kafkaConnectConsumerSpan.spanId)) {
      throw new AssertionError(
          "❌ Database span parent ("
              + databaseSpan.parentSpanId
              + ") does not match Kafka Connect Consumer span ("
              + kafkaConnectConsumerSpan.spanId
              + ")");
    }
    logger.info("✅ ASSERTION 3 PASSED: Database span is child of Kafka Connect Consumer span");

    logger.info("🎉 KAFKA CONNECT DISTRIBUTED TRACING ASSERTIONS COMPLETED!");
    logger.info("📊 Flow Summary:");
    logger.info(
        "   Consumer:  {} [{}] {}",
        kafkaConnectConsumerSpan.name,
        kafkaConnectConsumerSpan.spanId,
        hasSpanLinks ? "(has span links)" : "(no span links)");
    logger.info(
        "   Database:  {} [{}] (child of consumer)", databaseSpan.name, databaseSpan.spanId);
    logger.info("   Trace ID:  {}", kafkaConnectConsumerSpan.traceId);
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
