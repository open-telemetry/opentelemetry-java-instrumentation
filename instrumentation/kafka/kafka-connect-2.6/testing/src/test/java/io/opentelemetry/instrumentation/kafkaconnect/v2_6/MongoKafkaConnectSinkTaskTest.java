/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MongoKafkaConnectSinkTaskTest extends KafkaConnectSinkTaskTestBase {

  private static final Logger logger = LoggerFactory.getLogger(MongoKafkaConnectSinkTaskTest.class);

  // MongoDB-specific constants
  private static final String MONGO_NETWORK_ALIAS = "mongodb";
  private static final String DB_NAME = "testdb";
  private static final String COLLECTION_NAME = "person";
  private static final String CONNECTOR_NAME = "test-mongo-connector";
  private static final String TOPIC_NAME = "test-mongo-topic";

  // MongoDB container
  private static MongoDBContainer mongoDB;

  // Override abstract methods from base class
  @Override
  protected void setupDatabaseContainer() {
    mongoDB =
        new MongoDBContainer(DockerImageName.parse("mongo:4.4"))
            .withNetwork(network)
            .withNetworkAliases(MONGO_NETWORK_ALIAS)
            .withStartupTimeout(Duration.ofMinutes(5));
  }

  @Override
  protected void startDatabaseContainer() {
    Startables.deepStart(mongoDB).join();
  }

  @Override
  protected void stopDatabaseContainer() {
    if (mongoDB != null) {
      try {
        mongoDB.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping MongoDB: " + e.getMessage());
      }
    }
  }

  @Override
  protected void clearDatabaseData() {
    clearMongoCollection();
  }

  @Override
  protected String getConnectorInstallCommand() {
    return "confluent-hub install --no-prompt --component-dir /usr/share/java "
        + "mongodb/kafka-connect-mongodb:1.11.0";
  }

  @Override
  protected String getConnectorName() {
    return CONNECTOR_NAME;
  }

  @Override
  protected String getTopicName() {
    return TOPIC_NAME;
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

    // Use SmokeTestInstrumentationExtension's testing framework to wait for and assert traces
    // Wait for traces and then find the specific trace we want
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);

              // Find the trace that contains our Kafka Connect Consumer span and database INSERT
              // span
              List<SpanData> targetTrace =
                  traces.stream()
                      .filter(
                          trace -> {
                            boolean hasKafkaConnectSpan =
                                trace.stream()
                                    .anyMatch(
                                        span ->
                                            span.getName().contains(uniqueTopicName)
                                                && span.getKind()
                                                    == io.opentelemetry.api.trace.SpanKind
                                                        .CONSUMER);

                            boolean hasInsertSpan =
                                trace.stream()
                                    .anyMatch(
                                        span ->
                                            span.getName().contains("update")
                                                && span.getKind()
                                                    == io.opentelemetry.api.trace.SpanKind.CLIENT);

                            return hasKafkaConnectSpan && hasInsertSpan;
                          })
                      .findFirst()
                      .orElse(null);

              // Assert that we found the target trace
              assertThat(targetTrace).isNotNull();

              // Assert on the spans in the target trace (should have at least 2 spans: Kafka
              // Connect Consumer + database operations)
              assertThat(targetTrace).hasSizeGreaterThanOrEqualTo(2);

              // Find and assert the Kafka Connect Consumer span
              SpanData kafkaConnectSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              span.getName().contains(uniqueTopicName)
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER)
                      .findFirst()
                      .orElse(null);
              assertThat(kafkaConnectSpan).isNotNull();
              assertThat(kafkaConnectSpan.getParentSpanContext().isValid()).isFalse(); // No parent

              // Find and assert the database UPDATE span
              SpanData insertSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              span.getName().contains("update")
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT)
                      .findFirst()
                      .orElse(null);
              assertThat(insertSpan).isNotNull();
            });
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
      producer.send(
          new ProducerRecord<>(
              topicName1, "key1", "{\"id\":1,\"name\":\"User1\",\"source\":\"topic1\"}"));
      producer.send(
          new ProducerRecord<>(
              topicName2, "key2", "{\"id\":2,\"name\":\"User2\",\"source\":\"topic2\"}"));
      producer.send(
          new ProducerRecord<>(
              topicName3, "key3", "{\"id\":3,\"name\":\"User3\",\"source\":\"topic3\"}"));
      producer.flush();
    }

    // Wait for message processing (increased timeout for ARM64 Docker emulation)
    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 3);

    // Use SmokeTestInstrumentationExtension's testing framework to wait for and assert traces
    // Wait for traces and then find the specific trace we want
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);

              // Find the trace that contains our Kafka Connect Consumer span and database INSERT
              // span
              List<SpanData> targetTrace =
                  traces.stream()
                      .filter(
                          trace -> {
                            boolean hasKafkaConnectSpan =
                                trace.stream()
                                    .anyMatch(
                                        span ->
                                            (span.getName().contains(topicName1)
                                                    || span.getName().contains(topicName2)
                                                    || span.getName().contains(topicName3))
                                                && span.getKind()
                                                    == io.opentelemetry.api.trace.SpanKind
                                                        .CONSUMER);

                            boolean hasInsertSpan =
                                trace.stream()
                                    .anyMatch(
                                        span ->
                                            span.getName().contains("update")
                                                && span.getKind()
                                                    == io.opentelemetry.api.trace.SpanKind.CLIENT);

                            return hasKafkaConnectSpan && hasInsertSpan;
                          })
                      .findFirst()
                      .orElse(null);

              // Assert that we found the target trace
              assertThat(targetTrace).isNotNull();

              // Assert on the spans in the target trace (should have at least 2 spans: Kafka
              // Connect Consumer + database operations)
              assertThat(targetTrace).hasSizeGreaterThanOrEqualTo(2);

              // Find and assert the Kafka Connect Consumer span (multi-topic span)
              SpanData kafkaConnectSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              (span.getName().contains(topicName1)
                                      || span.getName().contains(topicName2)
                                      || span.getName().contains(topicName3))
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER)
                      .findFirst()
                      .orElse(null);
              assertThat(kafkaConnectSpan).isNotNull();
              assertThat(kafkaConnectSpan.getParentSpanContext().isValid()).isFalse(); // No parent

              // Find and assert the database UPDATE span
              SpanData insertSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              span.getName().contains("update")
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT)
                      .findFirst()
                      .orElse(null);
              assertThat(insertSpan).isNotNull();
            });
  }

  // MongoDB-specific helper methods
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
        MAPPER.writeValueAsString(
            ImmutableMap.of("name", CONNECTOR_NAME + "-multi", "config", configMap));
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
}
