/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractR2dbcResultConsumptionTest {

  protected abstract InstrumentationExtension getTesting();

  protected abstract ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions options);

  protected abstract Class<?> expectedUpdateCountType();

  protected abstract Number expectedUpdateCount();

  @ParameterizedTest
  @MethodSource("queryConsumptionScenarios")
  protected void queryResultConsumptionFinishesQuerySpan(
      Function<Result, Flux<Integer>> consumeResult) {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThat(
                              consumeResult
                                  .apply(
                                      Mono.from(connection.createStatement("SELECT 42").execute())
                                          .block(Duration.ofSeconds(10)))
                                  .single()
                                  .block(Duration.ofSeconds(10)))
                          .isEqualTo(42));
          assertQuerySpan("SELECT");
        });
  }

  @ParameterizedTest
  @MethodSource("updateCountConsumptionScenarios")
  protected void updateCountConsumptionFinishesQuerySpan(Function<Result, Flux<?>> consumeResult) {
    withConnection(
        connection -> {
          createTable(connection);
          getTesting()
              .runWithSpan(
                  "parent",
                  () -> {
                    List<?> counts =
                        Flux.from(
                                connection.createStatement("INSERT INTO test VALUES (1)").execute())
                            .flatMap(consumeResult)
                            .collectList()
                            .block(Duration.ofSeconds(10));
                    assertThat(counts).hasSize(1);
                    assertThat(counts.get(0)).isEqualTo(expectedUpdateCount());
                    assertThat(counts.get(0)).isExactlyInstanceOf(expectedUpdateCountType());
                  });
          assertQuerySpan("INSERT test");
        });
  }

  @Test
  protected void querySpanFinishesWhenResultConsumptionFails() {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThatThrownBy(
                              () ->
                                  Flux.from(
                                          Mono.from(
                                                  connection.createStatement("SELECT 42").execute())
                                              .flatMapMany(
                                                  result ->
                                                      result.map(
                                                          (row, metadata) -> {
                                                            throw new IllegalStateException("test");
                                                          })))
                                      .blockLast(Duration.ofSeconds(10)))
                          .isInstanceOf(IllegalStateException.class));
          assertQuerySpan("SELECT");
        });
  }

  @Test
  protected void querySpanFinishesWhenResultConsumptionIsCancelled() {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThat(
                              Flux.from(
                                      Mono.from(
                                              connection
                                                  .createStatement("SELECT 1 UNION ALL SELECT 2")
                                                  .execute())
                                          .flatMapMany(
                                              result ->
                                                  result.map(
                                                      (row, metadata) ->
                                                          row.get(0, Integer.class))))
                                  .take(1)
                                  .blockLast(Duration.ofSeconds(10)))
                          .isEqualTo(1));
          assertQuerySpan("SELECT SELECT");
        });
  }

  protected static Stream<Arguments> queryConsumptionScenarios() {
    return Stream.of(
        argumentSet(
            "map(BiFunction)",
            (Function<Result, Flux<Integer>>)
                result -> Flux.from(result.map((row, metadata) -> row.get(0, Integer.class)))),
        argumentSet(
            "map(Function)",
            (Function<Result, Flux<Integer>>)
                result -> Flux.from(result.map(row -> row.get(0, Integer.class)))),
        argumentSet(
            "filtered flatMap",
            (Function<Result, Flux<Integer>>)
                AbstractR2dbcResultConsumptionTest::consumeFilteredRows));
  }

  private static Flux<Integer> consumeFilteredRows(Result result) {
    return Flux.from(
        result
            .filter(segment -> segment instanceof Result.RowSegment)
            .flatMap(
                segment -> Mono.just(((Result.RowSegment) segment).row().get(0, Integer.class))));
  }

  protected static Stream<Arguments> updateCountConsumptionScenarios() {
    return Stream.of(
        argumentSet(
            "plain result",
            (Function<Result, Flux<?>>) result -> Flux.from(result.getRowsUpdated())),
        argumentSet(
            "filtered result",
            (Function<Result, Flux<?>>)
                result ->
                    Flux.from(
                        result
                            .filter(segment -> segment instanceof Result.UpdateCount)
                            .getRowsUpdated())));
  }

  private void withConnection(Consumer<Connection> action) {
    ConnectionFactoryOptions options =
        ConnectionFactoryOptions.parse("r2dbc:h2:mem:///result_consumption_" + UUID.randomUUID());
    Connection connection =
        Mono.from(createProxyConnectionFactory(options).create()).block(Duration.ofSeconds(10));
    try {
      action.accept(connection);
    } finally {
      Mono.from(connection.close()).block(Duration.ofSeconds(10));
    }
  }

  private void createTable(Connection connection) {
    Flux.from(connection.createStatement("CREATE TABLE test (id INT)").execute())
        .flatMap(result -> Flux.from(result.getRowsUpdated()))
        .then()
        .block(Duration.ofSeconds(10));
    getTesting().waitForTraces(1);
    getTesting().clearData();
  }

  private void assertQuerySpan(String queryName) {
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span ->
                        span.hasName(queryName)
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))));
  }
}
