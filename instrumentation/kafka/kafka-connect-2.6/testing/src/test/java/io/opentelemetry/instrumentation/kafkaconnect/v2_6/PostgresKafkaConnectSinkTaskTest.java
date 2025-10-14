/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresKafkaConnectSinkTaskTest extends KafkaConnectSinkTaskBaseTest {

  private static final Logger logger =
      LoggerFactory.getLogger(PostgresKafkaConnectSinkTaskTest.class);

  private static final String POSTGRES_NETWORK_ALIAS = "postgres";
  private static final String DB_NAME = "test";
  private static final String DB_USERNAME = "postgres";
  private static final String DB_PASSWORD = "password";
  private static final String DB_TABLE_PERSON = "person";
  private static final String CONNECTOR_NAME = "test-postgres-connector";
  private static final String TOPIC_NAME = "test-postgres-topic";

  private static PostgreSQLContainer<?> postgreSql;

  @Override
  protected void setupDatabaseContainer() {
    postgreSql =
        new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:11").asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases(POSTGRES_NETWORK_ALIAS)
            .withInitScript("postgres-setup.sql")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USERNAME)
            .withPassword(DB_PASSWORD)
            .withStartupTimeout(Duration.ofMinutes(5));
  }

  @Override
  protected void startDatabaseContainer() {
    Startables.deepStart(postgreSql).join();
  }

  @Override
  protected void stopDatabaseContainer() {
    if (postgreSql != null) {
      postgreSql.stop();
    }
  }

  @Override
  protected void clearDatabaseData() throws Exception {
    clearPostgresTable();
  }

  @Override
  protected String getConnectorInstallCommand() {
    return "confluent-hub install --no-prompt --component-dir /usr/share/java "
        + "confluentinc/kafka-connect-jdbc:10.7.4";
  }

  @Override
  protected String getConnectorName() {
    return CONNECTOR_NAME;
  }

  @Test
  public void testKafkaConnectPostgresSinkTaskInstrumentation() throws Exception {
    String testTopicName = TOPIC_NAME;
    setupPostgresSinkConnector(testTopicName);

    createTopic(testTopicName);
    awaitForTopicCreation(testTopicName);

    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaBoostrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    testing.waitForTraces(9); // Skip initial traces from Kafka Connect startup
    testing.clearData();

    try (Producer<String, String> producer = instrument(new KafkaProducer<>(props))) {
      producer.send(
          new ProducerRecord<>(
              testTopicName,
              "test-key",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":1,\"name\":\"TestUser\"}}"));
      producer.flush();
    }

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromPostgres() >= 1);

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
        trace -> {
          // kafka connect consumer trace, linked to producer span via a span link
          Consumer<SpanDataAssert> selectAssertion =
              span ->
                  span.hasName("SELECT test").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0));

          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(testTopicName + " process")
                      .hasKind(CONSUMER)
                      .hasNoParent()
                      .hasLinks(LinkData.create(producerSpanContext.get())),
              selectAssertion,
              selectAssertion,
              selectAssertion,
              selectAssertion,
              selectAssertion,
              span ->
                  span.hasName("INSERT test." + DB_TABLE_PERSON)
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0)));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()));
  }

  @Test
  public void testKafkaConnectPostgresSinkTaskMultiTopicInstrumentation() throws Exception {
    String topicName1 = TOPIC_NAME + "-1";
    String topicName2 = TOPIC_NAME + "-2";
    String topicName3 = TOPIC_NAME + "-3";

    setupPostgresSinkConnectorMultiTopic(topicName1, topicName2, topicName3);

    createTopic(topicName1);
    createTopic(topicName2);
    createTopic(topicName3);
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
              topicName1,
              "key1",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":1,\"name\":\"User1\"}}"));
      producer.send(
          new ProducerRecord<>(
              topicName2,
              "key2",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":2,\"name\":\"User2\"}}"));
      producer.send(
          new ProducerRecord<>(
              topicName3,
              "key3",
              "{\"schema\":{\"type\":\"struct\",\"fields\":[{\"field\":\"id\",\"type\":\"int32\"},{\"field\":\"name\",\"type\":\"string\"}]},\"payload\":{\"id\":3,\"name\":\"User3\"}}"));
      producer.flush();
    } finally {
      parentSpan.end();
    }

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromPostgres() >= 3);

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
        trace -> {
          // kafka connect consumer trace, linked to producer span via a span link
          Consumer<SpanDataAssert> selectAssertion =
              span ->
                  span.hasName("SELECT test").hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0));

          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("unknown process")
                      .hasKind(CONSUMER)
                      .hasNoParent()
                      .hasLinks(
                          LinkData.create(producerSpanContext1.get()),
                          LinkData.create(producerSpanContext2.get()),
                          LinkData.create(producerSpanContext3.get())),
              selectAssertion,
              selectAssertion,
              selectAssertion,
              selectAssertion,
              selectAssertion,
              span ->
                  span.hasName("INSERT test." + DB_TABLE_PERSON)
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0)));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /connectors").hasKind(SpanKind.SERVER).hasNoParent()));
  }

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

  private static void setupPostgresSinkConnectorMultiTopic(String... topicNames)
      throws IOException {
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
    // Configure multiple topics separated by commas
    configMap.put("topics", String.join(",", topicNames));
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

  private static long getRecordCountFromPostgres() throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + DB_TABLE_PERSON)) {
      if (rs.next()) {
        return rs.getLong(1);
      }
    }
    return 0;
  }

  private static void clearPostgresTable() throws SQLException {
    try (Connection conn =
            DriverManager.getConnection(postgreSql.getJdbcUrl(), DB_USERNAME, DB_PASSWORD);
        Statement st = conn.createStatement()) {
      st.executeUpdate("DELETE FROM " + DB_TABLE_PERSON);
      logger.info("Cleared PostgreSQL table: {}", DB_TABLE_PERSON);
    }
  }
}
