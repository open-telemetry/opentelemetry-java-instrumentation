/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;

import java.util.Locale;

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
import java.util.Date;
import java.util.HashMap;
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

  // Using the same fake backend pattern as smoke tests
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
        kafkaConnect.getHost(), kafkaConnect.getMappedPort(CONNECT_REST_PORT_INTERNAL));
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
    backend = new GenericContainer<>(DockerImageName.parse(
            "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20221127.3559314891"))
        .withExposedPorts(BACKEND_PORT)
        .withNetwork(network)
        .withNetworkAliases(BACKEND_ALIAS)
        .waitingFor(Wait.forHttp("/health").forPort(BACKEND_PORT).withStartupTimeout(Duration.of(5, MINUTES)))
        .withStartupTimeout(Duration.of(5, MINUTES))
        .withEnv("DOCKER_DEFAULT_PLATFORM", "linux/amd64"); // Force AMD64 for stability on ARM64 hosts
    
    logger.info("Starting backend container...");
    backend.start();
    logger.info("Backend container started at: http://{}:{}", 
        backend.getHost(), backend.getMappedPort(BACKEND_PORT));

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

    // Get the agent path from system properties
    String agentPath = System.getProperty("otel.javaagent.testing.javaagent-jar-path");
    if (agentPath == null) {
        throw new IllegalStateException("Agent path not found. Make sure the test is run with the agent.");
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
            .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("kafka-connect-container")))
            // Copy the agent jar to the container
            .withCopyFileToContainer(
                MountableFile.forHostPath(agentPath), 
                "/opentelemetry-javaagent.jar")
            // Configure the agent to export spans to backend (like smoke tests)
            .withEnv("JAVA_TOOL_OPTIONS", 
                "-javaagent:/opentelemetry-javaagent.jar " +
                "-Dotel.javaagent.debug=true")
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
            .withEnv("CONNECT_LOG4J_LOGGERS", "org.reflections=ERROR,org.apache.kafka.connect=DEBUG")
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
            .waitingFor(Wait.forHttp("/").forPort(CONNECT_REST_PORT_INTERNAL).withStartupTimeout(Duration.of(5, MINUTES)))
            .withStartupTimeout(Duration.of(5, MINUTES))
            .withCommand(
                "bash",
                "-c",
                "confluent-hub install --no-prompt --component-dir /usr/share/java "
                    + "mongodb/kafka-connect-mongodb:1.11.0 && /etc/confluent/docker/run");

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
      given()
          .when()
          .get(backendUrl + "/clear")
          .then()
          .statusCode(200);
    } catch (RuntimeException e) {
      // Ignore failures to clear traces
    }
  }

  private static String getBackendUrl() {
    return format(Locale.ROOT, "http://%s:%d", backend.getHost(), backend.getMappedPort(BACKEND_PORT));
  }

  @Test
  public void testKafkaConnectMongoSinkTaskInstrumentation() throws IOException, InterruptedException {
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
          new ProducerRecord<>(
              uniqueTopicName,
              "test-key",
              "{\"id\":1,\"name\":\"TestUser\"}"));
      producer.flush();
    }

    // Wait for message processing (increased timeout for ARM64 Docker emulation)
    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 1);

    // Wait for spans to arrive at backend (increased timeout for ARM64 Docker emulation)
    String backendUrl = getBackendUrl();
    await().atMost(Duration.ofSeconds(30)).until(() -> {
      try {
        String traces = given()
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

    // Retrieve and verify spans
    String tracesJson = given()
        .when()
        .get(backendUrl + "/get-traces")
        .then()
        .statusCode(200)
        .extract()
        .asString();

    // Parse and analyze spans
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode tracesNode = objectMapper.readTree(tracesJson);
    
    boolean foundKafkaConnectSpan = false;
    int spanCount = 0;
    
    for (JsonNode trace : tracesNode) {
      JsonNode resourceSpans = trace.get("resourceSpans");
      if (resourceSpans != null && resourceSpans.isArray()) {
        for (JsonNode resourceSpan : resourceSpans) {
          JsonNode scopeSpans = resourceSpan.get("scopeSpans");
          if (scopeSpans != null && scopeSpans.isArray()) {
            for (JsonNode scopeSpan : scopeSpans) {
              JsonNode spans = scopeSpan.get("spans");
              if (spans != null && spans.isArray()) {
                for (JsonNode span : spans) {
                  spanCount++;
                  
                  JsonNode nameNode = span.get("name");
                  if (nameNode != null) {
                    String spanName = nameNode.asText();
                    
                    // Check for Kafka Connect spans
                    if (spanName.toLowerCase(Locale.ROOT).contains("kafka") ||
                        spanName.toLowerCase(Locale.ROOT).contains("connect") || 
                        spanName.toLowerCase(Locale.ROOT).contains("put") ||
                        spanName.toLowerCase(Locale.ROOT).contains("sink")) {
                      foundKafkaConnectSpan = true;
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    
    // Verify spans were found
    assertThat(spanCount)
        .as("Should find at least one span")
        .isGreaterThan(0);
        
    assertThat(foundKafkaConnectSpan)
        .as("Should find at least one Kafka Connect span")
        .isTrue();
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
    configMap.put("document.id.strategy", "com.mongodb.kafka.connect.sink.processor.id.strategy.BsonOidStrategy");

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
      e.printStackTrace();
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
    
    // Small delay to ensure containers are fully stopped before next test
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    
    logger.info("Test cleanup complete");
  }
}
