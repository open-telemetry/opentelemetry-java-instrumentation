/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

abstract class AbstractR2dbcSpi09Test {

  protected abstract InstrumentationExtension getTesting();

  protected abstract ConnectionFactory createProxyConnectionFactory(
      ConnectionFactoryOptions options);

  @Test
  void consumesQueryResult() {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThat(
                              Flux.from(
                                      Mono.from(connection.createStatement("SELECT 42").execute())
                                          .flatMapMany(
                                              result ->
                                                  result.map(
                                                      (row, metadata) ->
                                                          row.get(0, Integer.class))))
                                  .single()
                                  .block(Duration.ofSeconds(10)))
                          .isEqualTo(42));
          assertQuerySpan();
        });
  }

  @Test
  void consumesQueryResultWithMapFunction() {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThat(
                              Flux.from(
                                      Mono.from(connection.createStatement("SELECT 42").execute())
                                          .flatMapMany(
                                              result ->
                                                  result.map(
                                                      readable -> readable.get(0, Integer.class))))
                                  .single()
                                  .block(Duration.ofSeconds(10)))
                          .isEqualTo(42));
          assertQuerySpan();
        });
  }

  @Test
  void consumesFilteredQueryResult() {
    withConnection(
        connection -> {
          getTesting()
              .runWithSpan(
                  "parent",
                  () ->
                      assertThat(
                              Flux.from(connection.createStatement("SELECT 42").execute())
                                  .flatMap(
                                      result ->
                                          Flux.from(
                                              result
                                                  .filter(
                                                      segment ->
                                                          segment instanceof Result.RowSegment)
                                                  .flatMap(
                                                      segment ->
                                                          Mono.just(
                                                              ((Result.RowSegment) segment)
                                                                  .row()
                                                                  .get(0, Integer.class)))))
                                  .collectList()
                                  .block(Duration.ofSeconds(10)))
                          .containsExactly(42));
          assertQuerySpan();
        });
  }

  @Test
  void consumesPlainUpdateCountAsInteger() {
    withConnection(
        connection -> {
          createTable(connection);
          getTesting()
              .runWithSpan(
                  "parent",
                  () -> {
                    List<Number> counts =
                        Flux.from(
                                connection.createStatement("INSERT INTO test VALUES (1)").execute())
                            .flatMap(
                                result ->
                                    Flux.from(result.getRowsUpdated()).map(Number.class::cast))
                            .collectList()
                            .block(Duration.ofSeconds(10));
                    assertIntegerUpdateCount(counts);
                  });
          assertQuerySpan();
        });
  }

  @Test
  void consumesFilteredUpdateCountAsInteger() {
    withConnection(
        connection -> {
          createTable(connection);
          getTesting()
              .runWithSpan(
                  "parent",
                  () -> {
                    List<Number> counts =
                        Flux.from(
                                connection.createStatement("INSERT INTO test VALUES (1)").execute())
                            .flatMap(
                                result ->
                                    Flux.from(
                                            result
                                                .filter(
                                                    segment ->
                                                        segment instanceof Result.UpdateCount)
                                                .getRowsUpdated())
                                        .map(Number.class::cast))
                            .collectList()
                            .block(Duration.ofSeconds(10));
                    assertIntegerUpdateCount(counts);
                  });
          assertQuerySpan();
        });
  }

  @Test
  void querySpanFinishesWhenResultConsumptionFails() {
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
          assertQuerySpan();
        });
  }

  @Test
  void querySpanFinishesWhenResultConsumptionIsCancelled() {
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
          assertQuerySpan();
        });
  }

  private void withConnection(Consumer<Connection> action) {
    ConnectionFactoryOptions options =
        ConnectionFactoryOptions.parse("r2dbc:h2:mem:///spi09_" + UUID.randomUUID());
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

  private static void assertIntegerUpdateCount(List<Number> counts) {
    assertThat(counts).containsExactly(1);
    assertThat(counts.get(0)).isInstanceOf(Integer.class);
  }

  private void assertQuerySpan() {
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                    span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(0))));
  }
}
