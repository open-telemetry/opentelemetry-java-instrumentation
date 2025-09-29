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

  // PostgreSQL-specific constants
  private static final String POSTGRES_NETWORK_ALIAS = "postgres";
  private static final String DB_NAME = "test";
  private static final String DB_USERNAME = "postgres";
  private static final String DB_PASSWORD = "password";
  private static final String DB_TABLE_PERSON = "person";
  private static final String CONNECTOR_NAME = "test-postgres-connector";
  private static final String TOPIC_NAME = "test-postgres-topic";

  // PostgreSQL container
  private static PostgreSQLContainer<?> postgreSql;

  // Override abstract methods from base class
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
                                            span.getName().equals("INSERT test." + DB_TABLE_PERSON)
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

              // Find and assert the database INSERT span
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

  // PostgreSQL-specific helper methods
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
