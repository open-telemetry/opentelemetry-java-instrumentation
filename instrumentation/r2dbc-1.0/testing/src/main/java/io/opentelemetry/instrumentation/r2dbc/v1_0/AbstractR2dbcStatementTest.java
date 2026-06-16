/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static io.r2dbc.spi.ConnectionFactoryOptions.CONNECT_TIMEOUT;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Named.named;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
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
                                equalTo(
                                    DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv()
                                        ? null
                                        : parameter.system + "://localhost:" + port),
                                equalTo(maybeStable(DB_SYSTEM), parameter.system),
                                equalTo(maybeStable(DB_NAME), DB),
                                equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                                equalTo(maybeStable(DB_STATEMENT), parameter.expectedQueryText),
                                equalTo(
                                    DB_QUERY_SUMMARY,
                                    emitStableDatabaseSemconv()
                                        ? parameter.getQuerySummary()
                                        : null),
                                equalTo(
                                    maybeStable(DB_OPERATION),
                                    emitStableDatabaseSemconv() ? null : parameter.operation),
                                equalTo(
                                    maybeStable(DB_SQL_TABLE),
                                    emitStableDatabaseSemconv() ? null : parameter.table),
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
                            new Parameter(
                                system.system,
                                "SELECT 3",
                                "SELECT ?",
                                emitStableDatabaseSemconv() ? "SELECT" : "SELECT " + DB,
                                null,
                                "SELECT"))),
                    Arguments.of(
                        named(
                            system.system + " Create Table",
                            new Parameter(
                                system.system,
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255))",
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(?), last_name VARCHAR(?))",
                                emitStableDatabaseSemconv()
                                    ? "CREATE TABLE person"
                                    : "CREATE TABLE " + DB + ".person",
                                "person",
                                "CREATE TABLE"))),
                    Arguments.of(
                        named(
                            system.system + " Insert",
                            new Parameter(
                                system.system,
                                "INSERT INTO person (id, first_name, last_name) values (1, 'tom', 'johnson')",
                                "INSERT INTO person (id, first_name, last_name) values (?, ?, ?)",
                                emitStableDatabaseSemconv()
                                    ? "INSERT person"
                                    : "INSERT " + DB + ".person",
                                "person",
                                "INSERT"))),
                    Arguments.of(
                        named(
                            system.system + " Select from Table",
                            new Parameter(
                                system.system,
                                "SELECT * FROM person where first_name = 'tom'",
                                "SELECT * FROM person where first_name = ?",
                                emitStableDatabaseSemconv()
                                    ? "SELECT person"
                                    : "SELECT " + DB + ".person",
                                "person",
                                "SELECT")))));
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
        emitStableDatabaseSemconv() ? DB_QUERY_SUMMARY : DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  // describes the batch cases: an empty batch (no statements -> error client span), a
  // single-statement batch (not a batch -> no db.operation.batch.size) and two statements with the
  // same operation. batch telemetry (db.operation.batch.size, BATCH span names and summaries) is
  // only emitted under stable database semconv; old semconv only sets statement-level attributes
  // (db.statement, db.operation, db.sql.table) for a single-statement batch
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
    // name be captured (in db.query.summary and, under old semconv, db.sql.table)
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

    String connectionString = MARIADB.system + "://localhost:" + port;

    if (scenario.queries.isEmpty()) {
      // an empty batch fails to execute and produces a client span with no operation, summary or
      // batch size; the span name falls back to the database namespace. under old semconv it still
      // carries db.user/db.connection_string and an empty db.statement; under stable semconv it
      // records the error instead (error.type is stable-only)
      assertThat(thrown).isInstanceOf(NoSuchElementException.class);
      getTesting()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                      span ->
                          span.hasName(DB)
                              .hasKind(SpanKind.CLIENT)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  equalTo(
                                      DB_CONNECTION_STRING,
                                      emitStableDatabaseSemconv() ? null : connectionString),
                                  equalTo(maybeStable(DB_SYSTEM), MARIADB.system),
                                  equalTo(maybeStable(DB_NAME), DB),
                                  equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                                  equalTo(
                                      maybeStable(DB_STATEMENT),
                                      emitStableDatabaseSemconv() ? null : ""),
                                  equalTo(maybeStablePeerService(), "test-peer-service"),
                                  equalTo(SERVER_ADDRESS, container.getHost()),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(
                                      ERROR_TYPE,
                                      emitStableDatabaseSemconv()
                                          ? "java.util.NoSuchElementException"
                                          : null))));
      return;
    }

    assertThat(thrown).isNull();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv()
                                    ? scenario.spanName
                                    : scenario.oldSpanName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv() ? null : connectionString),
                                equalTo(maybeStable(DB_SYSTEM), MARIADB.system),
                                equalTo(maybeStable(DB_NAME), DB),
                                equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                                // maybeStable(DB_STATEMENT) is db.query.text under stable semconv
                                // (identical query texts are deduplicated) and db.statement under
                                // old semconv (individual texts are concatenated with "; ")
                                equalTo(
                                    maybeStable(DB_STATEMENT),
                                    emitStableDatabaseSemconv()
                                        ? scenario.queryText
                                        : scenario.oldStatement),
                                equalTo(
                                    DB_QUERY_SUMMARY,
                                    emitStableDatabaseSemconv() ? scenario.summary : null),
                                equalTo(
                                    maybeStable(DB_OPERATION),
                                    emitStableDatabaseSemconv() ? null : scenario.oldOperation),
                                // db.sql.table is only set under old semconv and only for a
                                // single-statement batch (multi-statement batches do not capture a
                                // collection name)
                                equalTo(
                                    maybeStable(DB_SQL_TABLE),
                                    emitStableDatabaseSemconv() ? null : scenario.oldCollection),
                                equalTo(
                                    DB_OPERATION_BATCH_SIZE,
                                    emitStableDatabaseSemconv() ? scenario.batchSize : null),
                                equalTo(maybeStablePeerService(), "test-peer-service"),
                                equalTo(SERVER_ADDRESS, container.getHost()),
                                equalTo(SERVER_PORT, port))));
  }

  private static Stream<BatchScenario> batchScenarios() {
    return Stream.of(
        // an empty batch produces an error client span
        BatchScenario.builder("empty").queries(emptyList()).build(),
        // a single-statement batch is not a batch (size 1), so it emits no
        // db.operation.batch.size and no BATCH prefix; under old semconv it carries the
        // statement, operation, collection and the operation+namespace+table span name
        BatchScenario.builder("single")
            .queries(singletonList("INSERT INTO batch_test (id, num) VALUES (1, 1)"))
            .spanName("INSERT batch_test")
            .oldSpanName("INSERT " + DB + ".batch_test")
            .summary("INSERT batch_test")
            .queryText("INSERT INTO batch_test (id, num) VALUES (?, ?)")
            .oldStatement("INSERT INTO batch_test (id, num) VALUES (?, ?)")
            .oldOperation("INSERT")
            .oldCollection("batch_test")
            .build(),
        // a multi-statement batch emits the BATCH span name, deduplicated db.query.text and
        // db.operation.batch.size under stable semconv; the collection name is captured in the
        // summary (BATCH INSERT batch_test). under old semconv the individual statements are
        // concatenated but the shared operation and collection are still captured
        BatchScenario.builder("twoSameOperation")
            .queries(
                asList(
                    "INSERT INTO batch_test (id, num) VALUES (1, 1)",
                    "INSERT INTO batch_test (id, num) VALUES (2, 2)"))
            .spanName("BATCH INSERT batch_test")
            .oldSpanName("INSERT " + DB + ".batch_test")
            .summary("BATCH INSERT batch_test")
            .queryText("INSERT INTO batch_test (id, num) VALUES (?, ?)")
            .oldStatement(
                "INSERT INTO batch_test (id, num) VALUES (?, ?); INSERT INTO batch_test (id, num) VALUES (?, ?)")
            .oldOperation("INSERT")
            .oldCollection("batch_test")
            .batchSize(2)
            .build(),
        // a multi-statement batch with different operations has no shared operation or summary,
        // so db.query.summary (and the span name) is just BATCH; the individual statements are
        // still concatenated into db.query.text / db.statement
        BatchScenario.builder("twoDifferentOperations")
            .queries(
                asList(
                    "INSERT INTO batch_test (id, num) VALUES (1, 1)",
                    "UPDATE batch_test SET num = 5 WHERE id = 1"))
            .spanName("BATCH")
            .oldSpanName("INSERT " + DB + ".batch_test")
            .summary("BATCH")
            .queryText(
                "INSERT INTO batch_test (id, num) VALUES (?, ?); UPDATE batch_test SET num = ? WHERE id = ?")
            .oldStatement(
                "INSERT INTO batch_test (id, num) VALUES (?, ?); UPDATE batch_test SET num = ? WHERE id = ?")
            .oldOperation("INSERT")
            .oldCollection("batch_test")
            .batchSize(2)
            .build());
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
    private final String table;
    private final String operation;

    private Parameter(
        String system,
        String queryText,
        String expectedQueryText,
        String spanName,
        String table,
        String operation) {
      this.system = system;
      this.queryText = queryText;
      this.expectedQueryText = expectedQueryText;
      this.spanName = spanName;
      this.table = table;
      this.operation = operation;
    }

    private String getQuerySummary() {
      if (!emitStableDatabaseSemconv()) {
        return null;
      }
      // spanName contains the expected query summary for stable semconv
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
    final String name;
    final List<String> queries;
    final String spanName;
    final String oldSpanName;
    final String summary;
    // the stable-semconv db.query.text (identical query texts are deduplicated)
    final String queryText;
    // the old-semconv db.statement (individual query texts concatenated with "; ")
    final String oldStatement;
    final String oldOperation;
    // the old-semconv db.sql.table (only captured for a single-statement batch)
    final String oldCollection;
    final Long batchSize;

    BatchScenario(Builder builder) {
      this.name = builder.name;
      this.queries = builder.queries;
      this.spanName = builder.spanName;
      this.oldSpanName = builder.oldSpanName;
      this.summary = builder.summary;
      this.queryText = builder.queryText;
      this.oldStatement = builder.oldStatement;
      this.oldOperation = builder.oldOperation;
      this.oldCollection = builder.oldCollection;
      this.batchSize = builder.batchSize;
    }

    static Builder builder(String name) {
      return new Builder(name);
    }

    @Override
    public String toString() {
      // used as the parameterized test display name
      return name;
    }

    static final class Builder {
      private final String name;
      private List<String> queries;
      private String spanName;
      private String oldSpanName;
      private String summary;
      private String queryText;
      private String oldStatement;
      private String oldOperation;
      private String oldCollection;
      private Long batchSize;

      Builder(String name) {
        this.name = name;
      }

      Builder queries(List<String> queries) {
        this.queries = queries;
        return this;
      }

      Builder spanName(String spanName) {
        this.spanName = spanName;
        return this;
      }

      Builder oldSpanName(String oldSpanName) {
        this.oldSpanName = oldSpanName;
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

      Builder oldStatement(String oldStatement) {
        this.oldStatement = oldStatement;
        return this;
      }

      Builder oldOperation(String oldOperation) {
        this.oldOperation = oldOperation;
        return this;
      }

      Builder oldCollection(String oldCollection) {
        this.oldCollection = oldCollection;
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
