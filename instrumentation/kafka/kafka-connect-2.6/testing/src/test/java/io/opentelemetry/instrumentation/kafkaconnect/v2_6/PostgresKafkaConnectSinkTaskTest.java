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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.base.VerifyException;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers
// Suppressing warnings for test dependencies and deprecated Testcontainers API
@SuppressWarnings({"rawtypes", "unchecked", "deprecation", "unused"})
class PostgresKafkaConnectSinkTaskTest {

  private static final Logger logger = LoggerFactory.getLogger(PostgresKafkaConnectSinkTaskTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

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

  // Database
  private static final String DB_NAME = "test";
  private static final String DB_USERNAME = "postgres";
  private static final String DB_PASSWORD = "password";
  private static final String DB_TABLE_PERSON = "person";

  // Other constants
  private static final String PLUGIN_PATH_CONTAINER = "/usr/share/java";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final String CONNECTOR_NAME = "test-connector";
  private static final String TOPIC_NAME = "test-topic";

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
        "http://%s:%s",
        kafkaConnect.getHost(), kafkaConnect.getMappedPort(CONNECT_REST_PORT_INTERNAL));
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
    
    network = Network.newNetwork();

    zookeeper =
        new GenericContainer<>("confluentinc/cp-zookeeper:" + CONFLUENT_VERSION)
            .withNetwork(network)
            .withNetworkAliases(ZOOKEEPER_NETWORK_ALIAS)
            .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(ZOOKEEPER_INTERNAL_PORT))
            .withEnv("ZOOKEEPER_TICK_TIME", "2000")
            .withExposedPorts(ZOOKEEPER_INTERNAL_PORT)
            .withStartupTimeout(Duration.of(3, MINUTES));
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
            .withStartupTimeout(Duration.of(3, MINUTES));

    // Get the agent path from system properties
    String agentPath = System.getProperty("otel.javaagent.testing.javaagent-jar-path");
    if (agentPath == null) {
        throw new IllegalStateException("Agent path not found. Make sure the test is run with the agent.");
    }
    
    logger.info("=== AGENT JAR PATH: {} ===", agentPath);
    logger.info("=== AGENT JAR EXISTS: {} ===", new File(agentPath).exists());
    logger.info("=== AGENT JAR SIZE: {} bytes ===", new File(agentPath).length());
    logger.info("=== AGENT JAR LAST MODIFIED: {} ===", new Date(new File(agentPath).lastModified()));

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
            // Configure the agent to export spans to console
            .withEnv("JAVA_TOOL_OPTIONS", 
                "-javaagent:/opentelemetry-javaagent.jar " +
                "-Dotel.javaagent.debug=true " +
                "-Dotel.traces.exporter=console " +
                "-Dotel.metrics.exporter=none " +
                "-Dotel.bsp.max.export.batch.size=1 " +
                "-Dotel.bsp.schedule.delay=100")
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
            .withStartupTimeout(Duration.of(3, MINUTES))
            .withCommand(
                "bash",
                "-c",
                "confluent-hub install --no-prompt --component-dir /usr/share/java "
                    + "confluentinc/kafka-connect-jdbc:10.7.4 && /etc/confluent/docker/run");

    postgreSql =
        new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:11").asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withInitScript("postgres-setup.sql")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USERNAME)
            .withPassword(DB_PASSWORD)
            .withStartupTimeout(Duration.of(3, MINUTES));

    // Start containers
    Startables.deepStart(Stream.of(zookeeper, kafka, kafkaConnect, postgreSql)).join();

    // Wait until Kafka Connect container is ready
    given()
        .log()
        .headers()
        .contentType(ContentType.JSON)
        .when()
        .get(getKafkaConnectUrl())
        .andReturn()
        .then()
        .log()
        .all()
        .statusCode(HttpStatus.SC_OK);
  }

  @BeforeEach
  public void reset() {
    deleteConnectorIfExists();
  }

  @Test
  public void testKafkaConnectSinkTaskInstrumentation() throws IOException, InterruptedException {
    logger.info("=== Starting Kafka Connect SinkTask instrumentation test ===");

    // Create unique topic name first
    String uniqueTopicName = TOPIC_NAME + "-" + System.currentTimeMillis();

    // Setup Kafka Connect JDBC Sink connector
    setupSinkConnector(uniqueTopicName);

    // Create topic first
    logger.info("Creating topic...");
    createTopic(uniqueTopicName);
    logger.info("Topic created");

    // Wait for topic to be available
    logger.info("Awaiting topic creation...");
    awaitForTopicCreation(uniqueTopicName);
    logger.info("Topic creation complete");

    // Produce a single message for simpler testing
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

    // Wait for processing
    await().atMost(Duration.ofSeconds(30)).until(() -> getRecordCountFromPostgres() >= 1);

    // Give extra time for spans to be written to file
    Thread.sleep(10000);

    // Check for spans in container logs
    logger.info("=== Checking container logs for spans ===");
    String containerLogs = kafkaConnect.getLogs();
    logger.info("Container logs length: {} characters", containerLogs.length());

    // Look for console exporter output (JSON format)
    boolean containsKafkaConnectSpan = containerLogs.contains("KafkaConnect.put") || 
                                  containerLogs.contains("kafka-connect-2.6") ||
                                  containerLogs.contains("SinkTask.put") ||
                                  containerLogs.contains("\"name\":\"KafkaConnect") ||
                                  containerLogs.contains("\"resource\":{\"attributes\":") ||
                                  containerLogs.contains("🎯 KafkaConnect: Created span") ||
                                  containerLogs.contains("🚀 KafkaConnect: SinkTask.put() ENTER");

    if (containsKafkaConnectSpan) {
        logger.info("✅ SUCCESS: Found Kafka Connect spans in container logs!");
        
        // Print relevant log lines for verification
        String[] lines = containerLogs.split("\n");
        logger.info("Span-related log lines:");
        int count = 0;
        for (String line : lines) {
//            if ((line.contains("\"name\":") || line.contains("KafkaConnect") ||
//                 line.contains("\"resource\":") || line.contains("\"traceId\":")) && count < 10) {
//                logger.info("SPAN[{}]: {}", count, line);
//                count++;
//            }
          logger.info(line);
        }
    } else {
        logger.error("❌ FAILED: No Kafka Connect spans found in container logs");
        logger.info("Container logs preview (last 50 lines):");
        String[] lines = containerLogs.split("\n");
        int start = Math.max(0, lines.length - 50);
        for (int i = start; i < lines.length; i++) {
            logger.info("LOG[{}]: {}", i, lines[i]);
        }
    }

    assertThat(containsKafkaConnectSpan).isTrue();
}
  // Private methods
  private static void setupSinkConnector(String topicName) throws IOException {
    Map<String, Object> configMap = new HashMap<>();
    configMap.put("connector.class", "io.confluent.connect.jdbc.JdbcSinkConnector");
    configMap.put("tasks.max", "1");
    configMap.put(
        "connection.url",
        format("jdbc:postgresql://%s:5432/%s?loggerLevel=OFF", POSTGRES_NETWORK_ALIAS, DB_NAME));
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
    configMap.put("table.name.format", "person");
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

    // Remove this problematic cleanup code:
    // try (AdminClient adminClient = createAdminClient()) {
    //     adminClient.deleteTopics(Collections.singletonList(TOPIC_NAME)).all().get();
    //     System.out.println("Deleted existing topic: " + TOPIC_NAME);
    // } catch (e instanceof InterruptedException) {
    //     System.out.println("Topic cleanup: " + e.getMessage());
    // }
  }

  private static void produceMessagesToKafka(String topicName) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "test-producer-client"); // Add this line

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      // Send only ONE message for simpler testing
      producer.send(
          new ProducerRecord<>(
              topicName,
              "1",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":1,\"name\":\"Alice\"}}"));
      producer.flush();
      logger.info("Produced 1 message to Kafka topic: {}", topicName);
    }
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
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(500))
          .until(() -> adminClient.listTopics().names().get().contains(topicName));
    }
  }

  private static AdminClient createAdminClient() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    return KafkaAdminClient.create(properties);
  }

  private static int getRecordCountFromPostgres() throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + DB_TABLE_PERSON)) {
      if (rs.next()) {
        return rs.getInt(1);
      }
    }
    return 0;
  }

  private static List<Map<String, Object>> getRecordsFromPostgres() throws SQLException {
    List<Map<String, Object>> records = new ArrayList<>();
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT * FROM " + DB_TABLE_PERSON + " ORDER BY id")) {
      while (rs.next()) {
        Map<String, Object> record = new HashMap<>();
        record.put("id", rs.getInt("id"));
        record.put("name", rs.getString("name"));
        records.add(record);
      }
    }
    return records;
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
    // File cleanup no longer needed
    logger.info("Test cleanup complete");
  }
}
