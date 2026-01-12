/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static io.r2dbc.spi.ConnectionFactoryOptions.CONNECT_TIMEOUT;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.Experimental;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.listener.ProxyExecutionListener;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import reactor.core.publisher.Mono;

class SqlCommenterTest {
  private static final Logger logger = LoggerFactory.getLogger(SqlCommenterTest.class);

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final ContextPropagationOperator tracingOperator =
      ContextPropagationOperator.create();

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  private static Integer port;
  private static GenericContainer<?> container;

  @BeforeAll
  static void setup() {
    tracingOperator.registerOnEachOperator();

    container =
        new GenericContainer<>("mariadb:10.3.6")
            .withEnv("MYSQL_ROOT_PASSWORD", PW_DB)
            .withEnv("MYSQL_USER", USER_DB)
            .withEnv("MYSQL_PASSWORD", PW_DB)
            .withEnv("MYSQL_DATABASE", DB)
            .withExposedPorts(3306)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));

    container.start();
    port = container.getMappedPort(3306);
  }

  @AfterAll
  static void stop() {
    container.stop();
    tracingOperator.resetOnEachOperator();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSqlCommenter(boolean sqlCommenterEnabled) {
    ConnectionFactoryOptions options =
        ConnectionFactoryOptions.builder()
            .option(DRIVER, "mariadb")
            .option(HOST, container.getHost())
            .option(PORT, port)
            .option(USER, USER_DB)
            .option(PASSWORD, PW_DB)
            .option(DATABASE, DB)
            .option(CONNECT_TIMEOUT, Duration.ofSeconds(30))
            .build();
    ConnectionFactory original = ConnectionFactories.find(options);

    List<String> queries = new ArrayList<>();

    R2dbcTelemetryBuilder builder = R2dbcTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEnableSqlCommenter(builder, sqlCommenterEnabled);
    ConnectionFactory connectionFactory =
        builder
            .build()
            .wrapConnectionFactory(
                ProxyConnectionFactory.builder(original)
                    .listener(
                        new ProxyExecutionListener() {
                          @Override
                          public void beforeQuery(QueryExecutionInfo execInfo) {
                            for (QueryInfo queryInfo : execInfo.getQueries()) {
                              queries.add(queryInfo.getQuery());
                            }
                          }
                        })
                    .build(),
                options);

    SpanContext spanContext =
        testing.runWithSpan(
            "parent",
            () -> {
              Mono.from(connectionFactory.create())
                  .flatMapMany(
                      connection ->
                          Mono.from(connection.createStatement("SELECT 3").execute())
                              // Subscribe to the Statement.execute()
                              .flatMapMany(result -> result.map((row, metadata) -> ""))
                              .concatWith(Mono.from(connection.close()).cast(String.class)))
                  .doFinally(e -> testing.runWithSpan("child", () -> {}))
                  .blockLast(Duration.ofMinutes(1));
              return Span.current().getSpanContext();
            });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("SELECT " + DB)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("child").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));

    assertThat(queries).hasSize(1);
    if (sqlCommenterEnabled) {
      assertThat(queries.get(0))
          .contains("SELECT 3")
          .contains("traceparent")
          .contains(spanContext.getTraceId())
          .contains(spanContext.getSpanId());
    } else {
      assertThat(queries.get(0)).isEqualTo("SELECT 3");
    }
  }
}
