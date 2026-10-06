/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.CONNECT_TIMEOUT;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractR2dbcStatementTest {
  private static final Logger logger = LoggerFactory.getLogger(AbstractR2dbcStatementTest.class);

  protected abstract InstrumentationExtension getTesting();

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  private static final DbSystemProps POSTGRESQL =
      new DbSystemProps("postgresql", "postgres:9.6.8", 5432)
          .envVariables(
              "POSTGRES_USER", USER_DB,
              "POSTGRES_PASSWORD", PW_DB,
              "POSTGRES_DB", DB);

  private static final DbSystemProps MARIADB =
      new DbSystemProps("mariadb", "mariadb:10.3.6", 3306)
          .envVariables(
              "MYSQL_ROOT_PASSWORD", PW_DB,
              "MYSQL_USER", USER_DB,
              "MYSQL_PASSWORD", PW_DB,
              "MYSQL_DATABASE", DB);

  private static final DbSystemProps MYSQL =
      new DbSystemProps("mysql", "mysql:8.0.32", 3306)
          .envVariables(
              "MYSQL_ROOT_PASSWORD", PW_DB,
              "MYSQL_USER", USER_DB,
              "MYSQL_PASSWORD", PW_DB,
              "MYSQL_DATABASE", DB);

  private static final Map<String, DbSystemProps> systems = new LinkedHashMap<>();

  static {
    systems.put(POSTGRESQL.system, POSTGRESQL);
    systems.put(MYSQL.system, MYSQL);
    systems.put(MARIADB.system, MARIADB);
  }

  private static Integer port;
  private static GenericContainer<?> container;

  protected ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions connectionFactoryOptions) {
    return ConnectionFactories.find(connectionFactoryOptions);
  }

  @AfterAll
  void stopContainer() {
    if (container != null) {
      container.stop();
    }
  }

  void startContainer(DbSystemProps props) {
    if (container != null && container.getDockerImageName().equals(props.image)) {
      return;
    }
    if (container != null) {
      container.stop();
    }
    if (props.image != null) {
      container =
          new GenericContainer<>(props.image)
              .withEnv(props.envVariables)
              .withExposedPorts(props.port)
              .withLogConsumer(new Slf4jLogConsumer(logger))
              .withStartupTimeout(Duration.ofMinutes(2));
      if (props == POSTGRESQL) {
        container.waitingFor(
            Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
      }
      container.start();
      port = container.getMappedPort(props.port);
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideParameters")
  void testQueries(Parameter parameter) {
    DbSystemProps props = systems.get(parameter.system);
    startContainer(props);
    ConnectionFactory connectionFactory =
        createProxyConnectionFactory(
            ConnectionFactoryOptions.builder()
                .option(DRIVER, props.system)
                .option(HOST, container.getHost())
                .option(PORT, port)
                .option(USER, USER_DB)
                .option(PASSWORD, PW_DB)
                .option(DATABASE, DB)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(30))
                .build());

    getTesting()
        .runWithSpan(
            "parent",
            () -> {
              Mono.from(connectionFactory.create())
                  .flatMapMany(
                      connection ->
                          Mono.from(connection.createStatement(parameter.queryText).execute())
                              // Subscribe to the Statement.execute()
                              .flatMapMany(result -> result.map((row, metadata) -> ""))
                              .concatWith(Mono.from(connection.close()).cast(String.class)))
                  .doFinally(e -> getTesting().runWithSpan("child", () -> {}))
                  .blockLast(Duration.ofMinutes(1));
            });

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(parameter.spanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(DB_SYSTEM_NAME, parameter.system),
                                equalTo(DB_NAMESPACE, DB),
                                equalTo(DB_QUERY_TEXT, parameter.expectedQueryText),
                                equalTo(DB_QUERY_SUMMARY, parameter.getQuerySummary()),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(SERVER_ADDRESS, container.getHost()),
                                equalTo(SERVER_PORT, port)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  private static Stream<Arguments> provideParameters() {
    return systems.values().stream()
        .flatMap(
            system ->
                Stream.of(
                    Arguments.of(
                        named(
                            system.system + " Simple Select",
                            new Parameter(system.system, "SELECT 3", "SELECT ?", "SELECT"))),
                    Arguments.of(
                        named(
                            system.system + " Create Table",
                            new Parameter(
                                system.system,
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255))",
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(?), last_name VARCHAR(?))",
                                "CREATE TABLE person"))),
                    Arguments.of(
                        named(
                            system.system + " Insert",
                            new Parameter(
                                system.system,
                                "INSERT INTO person (id, first_name, last_name) values (1, 'tom', 'johnson')",
                                "INSERT INTO person (id, first_name, last_name) values (?, ?, ?)",
                                "INSERT person"))),
                    Arguments.of(
                        named(
                            system.system + " Select from Table",
                            new Parameter(
                                system.system,
                                "SELECT * FROM person where first_name = 'tom'",
                                "SELECT * FROM person where first_name = ?",
                                "SELECT person")))));
  }

  @Test
  void testMetrics() {
    DbSystemProps props = systems.get(MARIADB.system);
    startContainer(props);
    ConnectionFactory connectionFactory =
        createProxyConnectionFactory(
            ConnectionFactoryOptions.builder()
                .option(DRIVER, props.system)
                .option(HOST, container.getHost())
                .option(PORT, port)
                .option(USER, USER_DB)
                .option(PASSWORD, PW_DB)
                .option(DATABASE, DB)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(30))
                .build());

    Mono.from(connectionFactory.create())
        .flatMapMany(
            connection ->
                Mono.from(connection.createStatement("SELECT 3").execute())
                    .flatMapMany(result -> result.map((row, metadata) -> ""))
                    .concatWith(Mono.from(connection.close()).cast(String.class)))
        .blockLast(Duration.ofMinutes(1));

    assertDurationMetric(
        getTesting(),
        "io.opentelemetry.r2dbc-1.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_QUERY_SUMMARY,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchQueries(BatchScenario scenario) {
    DbSystemProps props = systems.get(MARIADB.system);
    startContainer(props);
    ConnectionFactory connectionFactory =
        createProxyConnectionFactory(
            ConnectionFactoryOptions.builder()
                .option(DRIVER, props.system)
                .option(HOST, container.getHost())
                .option(PORT, port)
                .option(USER, USER_DB)
                .option(PASSWORD, PW_DB)
                .option(DATABASE, DB)
                .option(CONNECT_TIMEOUT, Duration.ofSeconds(30))
                .build());

    // recreate a fresh batch_test table for each scenario so that batch row ids can be reused
    // without worrying about collisions from previous scenarios; the table also lets the collection
    // name be captured in db.query.summary
    recreateBatchTestTable(connectionFactory);
    getTesting().waitForTraces(2);
    getTesting().clearData();

    Throwable thrown =
        catchThrowable(
            () ->
                getTesting()
                    .runWithSpan(
                        "parent",
                        () -> {
                          Mono.from(connectionFactory.create())
                              .flatMapMany(
                                  connection -> {
                                    Batch batch = connection.createBatch();
                                    for (String query : scenario.queries) {
                                      batch.add(query);
                                    }
                                    return Flux.from(batch.execute())
                                        .flatMap(result -> result.map((row, metadata) -> ""))
                                        .concatWith(
                                            Mono.from(connection.close()).cast(String.class));
                                  })
                              .blockLast(Duration.ofMinutes(1));
                        }));

    if (scenario.queries.isEmpty()) {
      // an empty batch fails to execute but still produces a client span
      assertThat(thrown).isInstanceOf(NoSuchElementException.class);
      getTesting()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                      span ->
                          span.hasName("BATCH")
                              .hasKind(SpanKind.CLIENT)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(DB_SYSTEM_NAME, MARIADB.system),
                                  equalTo(DB_NAMESPACE, DB),
                                  equalTo(DB_QUERY_SUMMARY, "BATCH"),
                                  equalTo(DB_OPERATION_BATCH_SIZE, 0L),
                                  equalTo(maybeStablePeerService(), "test-peer-service"),
                                  equalTo(SERVER_ADDRESS, container.getHost()),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(ERROR_TYPE, "java.util.NoSuchElementException"))));
      return;
    }

    assertThat(thrown).isNull();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(scenario.spanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(DB_SYSTEM_NAME, MARIADB.system),
                                equalTo(DB_NAMESPACE, DB),
                                equalTo(DB_QUERY_TEXT, scenario.queryText),
                                equalTo(DB_QUERY_SUMMARY, scenario.summary),
                                equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(SERVER_ADDRESS, container.getHost()),
                                equalTo(SERVER_PORT, port))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        argumentSet("empty", BatchScenario.builder().build()),
        argumentSet(
            "single",
            BatchScenario.builder()
                .addQuery("INSERT INTO batch_test (id, num) VALUES (1, 1)")
                .spanName("INSERT batch_test")
                .summary("INSERT batch_test")
                .queryText("INSERT INTO batch_test (id, num) VALUES (?, ?)")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addQuery("INSERT INTO batch_test (id, num) VALUES (1, 1)")
                .addQuery("INSERT INTO batch_test (id, num) VALUES (2, 2)")
                .spanName("BATCH INSERT batch_test")
                .summary("BATCH INSERT batch_test")
                .queryText("INSERT INTO batch_test (id, num) VALUES (?, ?)")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addQuery("INSERT INTO batch_test (id, num) VALUES (1, 1)")
                .addQuery("UPDATE batch_test SET num = 5 WHERE id = 1")
                .spanName("BATCH")
                .summary("BATCH")
                .queryText(
                    "INSERT INTO batch_test (id, num) VALUES (?, ?); UPDATE batch_test SET num = ? WHERE id = ?")
                .batchSize(2)
                .build()));
  }

  private void recreateBatchTestTable(ConnectionFactory connectionFactory) {
    Mono.from(connectionFactory.create())
        .flatMapMany(
            connection ->
                Mono.from(connection.createStatement("DROP TABLE IF EXISTS batch_test").execute())
                    .flatMapMany(result -> result.map((row, metadata) -> ""))
                    .concatWith(
                        Mono.from(
                                connection
                                    .createStatement(
                                        "CREATE TABLE batch_test (id INTEGER PRIMARY KEY, num INTEGER)")
                                    .execute())
                            .flatMapMany(result -> result.map((row, metadata) -> "")))
                    .concatWith(Mono.from(connection.close()).cast(String.class)))
        .blockLast(Duration.ofMinutes(1));
  }

  private static class Parameter {

    private final String system;
    private final String queryText;
    private final String expectedQueryText;
    private final String spanName;

    private Parameter(String system, String queryText, String expectedQueryText, String spanName) {
      this.system = system;
      this.queryText = queryText;
      this.expectedQueryText = expectedQueryText;
      this.spanName = spanName;
    }

    private String getQuerySummary() {
      return spanName;
    }
  }

  private static class DbSystemProps {
    private final String system;
    private final String image;
    private final int port;
    private final Map<String, String> envVariables = new HashMap<>();

    private DbSystemProps(String system, String image, int port) {
      this.system = system;
      this.image = image;
      this.port = port;
    }

    @CanIgnoreReturnValue
    private DbSystemProps envVariables(String... keyValues) {
      for (int i = 0; i < keyValues.length / 2; i++) {
        envVariables.put(keyValues[2 * i], keyValues[2 * i + 1]);
      }
      return this;
    }
  }

  private static final class BatchScenario {
    final List<String> queries;
    final String spanName;
    final String summary;
    final String queryText;
    final Long batchSize;

    BatchScenario(Builder builder) {
      this.queries = builder.queries;
      this.spanName = builder.spanName;
      this.summary = builder.summary;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private final List<String> queries = new ArrayList<>();
      private String spanName;
      private String summary;
      private String queryText;
      private Long batchSize;

      Builder addQuery(String query) {
        queries.add(query);
        return this;
      }

      Builder spanName(String spanName) {
        this.spanName = spanName;
        return this;
      }

      Builder summary(String summary) {
        this.summary = summary;
        return this;
      }

      Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
