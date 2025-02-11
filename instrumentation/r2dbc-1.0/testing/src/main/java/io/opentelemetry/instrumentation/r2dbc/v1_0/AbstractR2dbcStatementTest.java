/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.junit.jupiter.api.Named.named;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
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

  private static final Map<String, DbSystemProps> SYSTEMS = new HashMap<>();

  static {
    SYSTEMS.put(POSTGRESQL.system, POSTGRESQL);
    SYSTEMS.put(MYSQL.system, MYSQL);
    SYSTEMS.put(MARIADB.system, MARIADB);
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

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideParameters")
  void testQueries(Parameter parameter) {
    DbSystemProps props = SYSTEMS.get(parameter.system);
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
                .build());

    getTesting()
        .runWithSpan(
            "parent",
            () -> {
              Mono.from(connectionFactory.create())
                  .flatMapMany(
                      connection ->
                          Mono.from(connection.createStatement(parameter.statement).execute())
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
                                equalTo(maybeStable(DB_STATEMENT), parameter.expectedStatement),
                                equalTo(maybeStable(DB_OPERATION), parameter.operation),
                                equalTo(maybeStable(DB_SQL_TABLE), parameter.table),
                                equalTo(SERVER_ADDRESS, container.getHost()),
                                equalTo(SERVER_PORT, port)),
                    span ->
                        span.hasName("child")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  private static Stream<Arguments> provideParameters() {
    return SYSTEMS.values().stream()
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
                                "SELECT " + DB,
                                null,
                                "SELECT"))),
                    Arguments.of(
                        named(
                            system.system + " Create Table",
                            new Parameter(
                                system.system,
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(255), last_name VARCHAR(255))",
                                "CREATE TABLE person (id SERIAL PRIMARY KEY, first_name VARCHAR(?), last_name VARCHAR(?))",
                                "CREATE TABLE " + DB + ".person",
                                "person",
                                "CREATE TABLE"))),
                    Arguments.of(
                        named(
                            system.system + " Insert",
                            new Parameter(
                                system.system,
                                "INSERT INTO person (id, first_name, last_name) values (1, 'tom', 'johnson')",
                                "INSERT INTO person (id, first_name, last_name) values (?, ?, ?)",
                                "INSERT " + DB + ".person",
                                "person",
                                "INSERT"))),
                    Arguments.of(
                        named(
                            system.system + " Select from Table",
                            new Parameter(
                                system.system,
                                "SELECT * FROM person where first_name = 'tom'",
                                "SELECT * FROM person where first_name = ?",
                                "SELECT " + DB + ".person",
                                "person",
                                "SELECT")))));
  }

  private static class Parameter {

    public final String system;
    public final String statement;
    public final String expectedStatement;
    public final String spanName;
    public final String table;
    public final String operation;

    public Parameter(
        String system,
        String statement,
        String expectedStatement,
        String spanName,
        String table,
        String operation) {
      this.system = system;
      this.statement = statement;
      this.expectedStatement = expectedStatement;
      this.spanName = spanName;
      this.table = table;
      this.operation = operation;
    }
  }

  private static class DbSystemProps {
    public final String system;
    public final String image;
    public final int port;
    public final Map<String, String> envVariables = new HashMap<>();

    public DbSystemProps(String system, String image, int port) {
      this.system = system;
      this.image = image;
      this.port = port;
    }

    @CanIgnoreReturnValue
    public DbSystemProps envVariables(String... keyValues) {
      for (int i = 0; i < keyValues.length / 2; i++) {
        envVariables.put(keyValues[2 * i], keyValues[2 * i + 1]);
      }
      return this;
    }
  }
}
