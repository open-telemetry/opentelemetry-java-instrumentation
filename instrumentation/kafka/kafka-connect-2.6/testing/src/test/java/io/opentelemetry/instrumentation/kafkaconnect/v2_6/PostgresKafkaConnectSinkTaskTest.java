/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaconnect.v2_6;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.trace.data.SpanData;
import io.restassured.http.ContentType;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresKafkaConnectSinkTaskTest extends KafkaConnectSinkTaskTestBase {

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
      try {
        postgreSql.stop();
      } catch (RuntimeException e) {
        logger.error("Error stopping PostgreSQL: " + e.getMessage());
      }
    }
  }

  @Override
  protected void clearDatabaseData() {
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

  @Override
  protected String getTopicName() {
    return TOPIC_NAME;
  }

  @Test
  public void testKafkaConnectPostgresSinkTaskInstrumentation()
      throws IOException, InterruptedException {
    String uniqueTopicName = TOPIC_NAME + "-" + System.currentTimeMillis();
    setupPostgresSinkConnector(uniqueTopicName);

    createTopic(uniqueTopicName);
    awaitForTopicCreation(uniqueTopicName);

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

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromPostgres() >= 1);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);

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
                                            span.getName().equals("INSERT test." + DB_TABLE_PERSON)
                                                && span.getKind()
                                                    == io.opentelemetry.api.trace.SpanKind.CLIENT);

                            return hasKafkaConnectSpan && hasInsertSpan;
                          })
                      .findFirst()
                      .orElse(null);

              assertThat(targetTrace).isNotNull();

              assertThat(targetTrace).hasSizeGreaterThanOrEqualTo(2);

              SpanData kafkaConnectSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              span.getName().contains(uniqueTopicName)
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER)
                      .findFirst()
                      .orElse(null);
              assertThat(kafkaConnectSpan).isNotNull();
              assertThat(kafkaConnectSpan.getParentSpanContext().isValid()).isFalse();

              SpanData insertSpan =
                  targetTrace.stream()
                      .filter(
                          span ->
                              span.getName().equals("INSERT test." + DB_TABLE_PERSON)
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT)
                      .findFirst()
                      .orElse(null);
              assertThat(insertSpan).isNotNull();
            });
  }

  @Test
  public void testKafkaConnectPostgresSinkTaskMultiTopicInstrumentation()
      throws IOException, InterruptedException {
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

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
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
    }

    await().atMost(Duration.ofSeconds(60)).until(() -> getRecordCountFromPostgres() >= 3);

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              // Wait for at least 1 trace (could be 1 batch trace or multiple individual traces)
              List<List<SpanData>> traces = testing.waitForTraces(1);

              // Count total Kafka Connect consumer spans and database INSERT spans across all
              // traces
              long totalKafkaConnectSpans =
                  traces.stream()
                      .flatMap(trace -> trace.stream())
                      .filter(
                          span ->
                              (span.getName().contains(topicName1)
                                      || span.getName().contains(topicName2)
                                      || span.getName().contains(topicName3))
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER)
                      .count();

              long totalInsertSpans =
                  traces.stream()
                      .flatMap(trace -> trace.stream())
                      .filter(
                          span ->
                              span.getName().equals("INSERT test." + DB_TABLE_PERSON)
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CLIENT)
                      .count();

              // PostgreSQL JDBC batches INSERT operations, so we expect at least 1 INSERT span
              // (unlike MongoDB which creates separate spans for each update)
              assertThat(totalKafkaConnectSpans).isGreaterThanOrEqualTo(1);
              assertThat(totalInsertSpans).isGreaterThanOrEqualTo(1);

              boolean hasMultiTopicSpan =
                  traces.stream()
                      .flatMap(trace -> trace.stream())
                      .anyMatch(
                          span ->
                              span.getName().contains("[")
                                  && span.getName().contains("]")
                                  && span.getName().contains("process")
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER);

              boolean hasIndividualSpans =
                  traces.stream()
                      .flatMap(trace -> trace.stream())
                      .anyMatch(
                          span ->
                              (span.getName().contains(topicName1)
                                      || span.getName().contains(topicName2)
                                      || span.getName().contains(topicName3))
                                  && !span.getName().contains("[")
                                  && span.getKind() == io.opentelemetry.api.trace.SpanKind.CONSUMER);

              assertThat(hasMultiTopicSpan || hasIndividualSpans).isTrue();
            });
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
}
