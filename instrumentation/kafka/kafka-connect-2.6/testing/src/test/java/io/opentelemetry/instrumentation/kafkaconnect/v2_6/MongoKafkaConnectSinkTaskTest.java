/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.PROCESS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID;
import static io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@Testcontainers
class MongoKafkaConnectSinkTaskTest extends KafkaConnectSinkTaskBaseTest {
  // MongoDB-specific constants
  private static final String MONGO_NETWORK_ALIAS = "mongodb";
  private static final String DB_NAME = "testdb";
  private static final String COLLECTION_NAME = "person";
  private static final String CONNECTOR_NAME = "test-mongo-connector";
  private static final String TOPIC_NAME = "test-mongo-topic";

  private static MongoDBContainer mongoDB;

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
      mongoDB.stop();
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

  @Test
  void testSingleMessage() throws Exception {
    String testTopicName = TOPIC_NAME;
    setupMongoSinkConnector(testTopicName);
    awaitForTopicCreation(testTopicName);

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    testing.waitForTraces(9); // Skip initial traces from Kafka Connect startup
    testing.clearData();

    try (Producer<String, String> producer = instrument(new KafkaProducer<>(props))) {
      producer.send(
          new ProducerRecord<>(testTopicName, "test-key", "{\"id\":1,\"name\":\"TestUser\"}"));
      producer.flush();
    }

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 1);

    AtomicReference<SpanContext> producerSpanContext = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            // producer is in a separate trace, linked to consumer with a span link
            trace.hasSpansSatisfyingExactly(
                span -> {
                  span.hasName(testTopicName + " publish").hasKind(SpanKind.PRODUCER).hasNoParent();
                  producerSpanContext.set(span.actual().getSpanContext());
                }),
        trace ->
            // kafka connect sends message to status topic while processing our message
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("kafka-connect-status publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent(),
                span ->
                    span.hasName("kafka-connect-status process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))),
        trace ->
            // kafka connect consumer trace, linked to producer span via a span link
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(testTopicName + " process")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(LinkData.create(producerSpanContext.get()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                            equalTo(MESSAGING_DESTINATION_NAME, testTopicName),
                            equalTo(MESSAGING_OPERATION, PROCESS),
                            equalTo(MESSAGING_SYSTEM, KAFKA),
                            satisfies(THREAD_ID, val -> val.isNotZero()),
                            satisfies(THREAD_NAME, val -> val.isNotBlank())),
                span ->
                    span.hasName("update " + DB_NAME + "." + COLLECTION_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()));
  }

  @Test
  void testMultiTopic() throws Exception {
    String topicName1 = TOPIC_NAME + "-1";
    String topicName2 = TOPIC_NAME + "-2";
    String topicName3 = TOPIC_NAME + "-3";

    setupMongoSinkConnectorMultiTopic(topicName1, topicName2, topicName3);
    awaitForTopicCreation(topicName1);
    awaitForTopicCreation(topicName2);
    awaitForTopicCreation(topicName3);

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 10); // to send messages in one batch
    props.put(ProducerConfig.LINGER_MS_CONFIG, 10_000); // 10 seconds

    testing.waitForTraces(9); // Skip initial traces from Kafka Connect startup
    testing.clearData();

    Span parentSpan = openTelemetry.getTracer("test").spanBuilder("parent").startSpan();
    try (Producer<String, String> producer = instrument(new KafkaProducer<>(props));
        Scope ignore = parentSpan.makeCurrent()) {
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
    } finally {
      parentSpan.end();
    }

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromMongo() >= 3);

    Consumer<TraceAssert> kafkaStatusAssertion =
        trace ->
            // kafka connect sends message to status topic while processing our message
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("kafka-connect-status publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasNoParent(),
                span ->
                    span.hasName("kafka-connect-status process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0)));

    AtomicReference<SpanContext> producerSpanContext1 = new AtomicReference<>();
    AtomicReference<SpanContext> producerSpanContext2 = new AtomicReference<>();
    AtomicReference<SpanContext> producerSpanContext3 = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace ->
            // producer is in a separate trace, linked to consumer with a span link
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent(),
                span -> {
                  span.hasName(topicName1 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0));
                  producerSpanContext1.set(span.actual().getSpanContext());
                },
                span -> {
                  span.hasName(topicName2 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0));
                  producerSpanContext2.set(span.actual().getSpanContext());
                },
                span -> {
                  span.hasName(topicName3 + " publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0));
                  producerSpanContext3.set(span.actual().getSpanContext());
                }),
        kafkaStatusAssertion,
        kafkaStatusAssertion,
        kafkaStatusAssertion,
        trace ->
            // kafka connect consumer trace, linked to producer span via a span link
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("unknown process")
                        .hasKind(CONSUMER)
                        .hasNoParent()
                        .hasLinks(
                            LinkData.create(producerSpanContext1.get()),
                            LinkData.create(producerSpanContext2.get()),
                            LinkData.create(producerSpanContext3.get()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3),
                            equalTo(MESSAGING_OPERATION, PROCESS),
                            equalTo(MESSAGING_SYSTEM, KAFKA),
                            satisfies(THREAD_ID, val -> val.isNotZero()),
                            satisfies(THREAD_NAME, val -> val.isNotBlank())),
                span ->
                    span.hasName("update " + DB_NAME + "." + COLLECTION_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("update " + DB_NAME + "." + COLLECTION_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("update " + DB_NAME + "." + COLLECTION_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()));
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
    }
  }

  private static void clearMongoCollection() {
    try (MongoClient mongoClient = MongoClients.create(mongoDB.getConnectionString())) {
      MongoDatabase database = mongoClient.getDatabase(DB_NAME);
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);
      collection.drop();
    }
  }
}
