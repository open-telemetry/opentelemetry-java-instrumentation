/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPipeline;

@SuppressWarnings("deprecation") // using deprecated semconv
class JedisClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String host;

  private static int port;

  private static Jedis jedis;

  @BeforeAll
  static void setup() {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    jedis = new Jedis(host, port);
    cleanup.deferAfterAll(jedis::disconnect);
  }

  @BeforeEach
  void reset() {
    jedis.select(0);
    jedis.flushAll();
    testing.clearData();
  }

  @Test
  void setCommand() {
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.jedis-1.4",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void getCommand() {
    jedis.set("foo", "bar");
    String value = jedis.get("foo");

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  @Test
  void commandWithNoArguments() {
    jedis.set("foo", "bar");
    String value = jedis.randomKey();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY"),
                            equalTo(maybeStable(DB_OPERATION), "RANDOMKEY"),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void pipelineCommand(BatchScenario scenario) {
    jedis.pipelined(new PipelineRunner(scenario));

    if (scenario.operationName == null) {
      assertThat(testing.spans()).isEmpty();
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.operationName + " " + host + ":" + port
                                : scenario.operationName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), scenario.queryText),
                            equalTo(maybeStable(DB_OPERATION), scenario.operationName),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(maybeStablePeerService(), "test-peer-service"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        // no span is created for empty pipelines
        Arguments.argumentSet("empty", BatchScenario.builder().build()),
        Arguments.argumentSet(
            "single",
            BatchScenario.builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        Arguments.argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.set("batch2", "v2"))
                .operationName("PIPELINE SET")
                .queryText("SET batch1 ?;SET batch2 ?")
                .batchSize(2)
                .build()),
        Arguments.argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.get("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?;GET batch1")
                .batchSize(2)
                .build()));
  }

  interface PipelineOperations {
    void set(String key, String value);

    void get(String key);
  }

  private static class PipelineRunner extends JedisPipeline {
    private final BatchScenario scenario;

    private PipelineRunner(BatchScenario scenario) {
      this.scenario = scenario;
    }

    @Override
    public void execute() {
      scenario.pipeline.accept(
          new PipelineOperations() {
            @Override
            public void set(String key, String value) {
              client.set(key, value);
            }

            @Override
            public void get(String key) {
              client.get(key);
            }
          });
    }
  }

  static class BatchScenario {
    final Consumer<PipelineOperations> pipeline;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;

    private BatchScenario(Builder builder) {
      this.pipeline = builder.pipeline;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    private static Builder builder() {
      return new Builder();
    }

    private static class Builder {
      private Consumer<PipelineOperations> pipeline = pipeline -> {};
      private String operationName;
      private String queryText;
      private Long batchSize;

      private Builder addCommand(Consumer<PipelineOperations> command) {
        this.pipeline = this.pipeline.andThen(command);
        return this;
      }

      private Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      private Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      private Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
