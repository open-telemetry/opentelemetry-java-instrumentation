/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Builder;
import redis.clients.jedis.Client;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;

class Jedis23ReplyIsolationTest {
  private static final String WRONG_TYPE_KEY = "nested-wrong-type";

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
    jedis.flushAll();
    testing.clearData();
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void caughtRawPipelineReplyDoesNotFailSuccessfulBatch(boolean readMany, boolean sameConnection) {
    Jedis nestedClient = prepareNestedClient();
    List<Throwable> nestedErrors = new ArrayList<>();
    Client connection = sameConnection ? jedis.getClient() : nestedClient.getClient();
    ReplyPipeline pipeline = new ReplyPipeline(connection, readMany, false, nestedErrors);
    pipeline.setClient(jedis.getClient());
    pipeline.ping();
    pipeline.set("outer-pipeline", "value");

    assertThat(pipeline.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertNestedErrors(nestedErrors);
    assertBatchErrorType(null, "PIPELINE");
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void escapingRawPipelineReplyFailsOuterBatch(boolean readMany, boolean sameConnection) {
    Jedis nestedClient = prepareNestedClient();
    List<Throwable> nestedErrors = new ArrayList<>();
    Client connection = sameConnection ? jedis.getClient() : nestedClient.getClient();
    ReplyPipeline pipeline = new ReplyPipeline(connection, readMany, true, nestedErrors);
    pipeline.setClient(jedis.getClient());
    pipeline.ping();
    pipeline.set("outer-pipeline", "value");

    List<Object> results = pipeline.syncAndReturnAll();

    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageContaining("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertNestedErrors(nestedErrors);
    assertBatchErrorType("WRONGTYPE", "PIPELINE");
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void caughtRawTransactionReplyDoesNotFailSuccessfulBatch(
      boolean readMany, boolean sameConnection) {
    Jedis nestedClient = prepareNestedClient();
    List<Throwable> nestedErrors = new ArrayList<>();
    Client connection = sameConnection ? jedis.getClient() : nestedClient.getClient();
    jedis.getClient().multi();
    assertThat(jedis.getClient().getAll()).hasSize(1);
    ReplyTransaction transaction = new ReplyTransaction(connection, readMany, false, nestedErrors);
    transaction.ping();
    transaction.set("outer-transaction", "value");
    testing.clearData();

    assertThat(transaction.exec()).containsExactly("PONG", "OK");
    assertNestedErrors(nestedErrors);
    assertBatchErrorType(null, "MULTI");
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void escapingRawTransactionReplyFailsOuterBatch(boolean readMany, boolean sameConnection) {
    Jedis nestedClient = prepareNestedClient();
    List<Throwable> nestedErrors = new ArrayList<>();
    Client connection = sameConnection ? jedis.getClient() : nestedClient.getClient();
    jedis.getClient().multi();
    assertThat(jedis.getClient().getAll()).hasSize(1);
    ReplyTransaction transaction = new ReplyTransaction(connection, readMany, true, nestedErrors);
    transaction.ping();
    transaction.set("outer-transaction", "value");
    testing.clearData();

    List<Object> results = transaction.exec();

    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageContaining("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertNestedErrors(nestedErrors);
    assertBatchErrorType("WRONGTYPE", "MULTI");
  }

  @ParameterizedTest
  @CsvSource({"false", "true"})
  void outerPipelineErrorsRemainCaptured(boolean returnAll) {
    jedis.lpush("outer-wrong-type", "value");
    Pipeline pipeline = jedis.pipelined();
    Response<String> response = pipeline.get("outer-wrong-type");
    pipeline.set("after-error", "value");
    testing.clearData();

    if (returnAll) {
      List<Object> results = pipeline.syncAndReturnAll();
      assertThat(results.get(0)).isInstanceOf(JedisDataException.class);
      assertThat(results.get(1)).isEqualTo("OK");
    } else {
      pipeline.sync();
      assertThatThrownBy(response::get).hasMessageContaining("WRONGTYPE");
    }

    assertBatchErrorType("WRONGTYPE", "PIPELINE");
  }

  @Test
  void outerTransactionErrorsRemainCaptured() {
    jedis.lpush("outer-wrong-type", "value");
    jedis.getClient().multi();
    assertThat(jedis.getClient().getAll()).hasSize(1);
    Transaction transaction = new Transaction(jedis.getClient());
    transaction.get("outer-wrong-type");
    transaction.set("after-error", "value");
    testing.clearData();

    List<Object> results = transaction.exec();

    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageContaining("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertBatchErrorType("WRONGTYPE", "MULTI");
  }

  private static Jedis prepareNestedClient() {
    jedis.lpush(WRONG_TYPE_KEY, "value");
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();
    return nestedClient;
  }

  private static Throwable readNestedError(Client connection, boolean readMany) {
    if (readMany) {
      connection.ping();
      connection.get(WRONG_TYPE_KEY);
      List<Object> replies = connection.getAll();
      assertThat(replies).hasSize(2);
      assertThat(new String((byte[]) replies.get(0), UTF_8)).isEqualTo("PONG");
      return (Throwable) replies.get(1);
    }

    connection.get(WRONG_TYPE_KEY);
    try {
      connection.getBulkReply();
      throw new AssertionError("Expected WRONGTYPE");
    } catch (JedisDataException error) {
      return error;
    }
  }

  private static void assertNestedErrors(List<Throwable> nestedErrors) {
    assertThat(nestedErrors)
        .singleElement()
        .satisfies(error -> assertThat(error).hasMessageContaining("WRONGTYPE"));
  }

  private static void assertBatchErrorType(String errorType, String batchOperation) {
    testing.waitForTraces(1);
    assertThat(testing.spans())
        .filteredOn(span -> batchOperation.equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo(errorType));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-2.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point ->
                                batchOperation.equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .singleElement()
                        .satisfies(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }

  private static class ReplyPipeline extends Pipeline {
    private final Client connection;
    private final boolean readMany;
    private final boolean throwError;
    private final List<Throwable> nestedErrors;
    private boolean read;

    private ReplyPipeline(
        Client connection, boolean readMany, boolean throwError, List<Throwable> nestedErrors) {
      this.connection = connection;
      this.readMany = readMany;
      this.throwError = throwError;
      this.nestedErrors = nestedErrors;
    }

    @Override
    protected <T> Response<T> getResponse(Builder<T> builder) {
      return super.getResponse(
          new Builder<T>() {
            @Override
            public T build(Object response) {
              if (!read) {
                read = true;
                Throwable error = readNestedError(connection, readMany);
                nestedErrors.add(error);
                if (throwError) {
                  throw (JedisDataException) error;
                }
              }
              return builder.build(response);
            }
          });
    }
  }

  private static class ReplyTransaction extends Transaction {
    private final Client connection;
    private final boolean readMany;
    private final boolean throwError;
    private final List<Throwable> nestedErrors;
    private boolean read;

    private ReplyTransaction(
        Client connection, boolean readMany, boolean throwError, List<Throwable> nestedErrors) {
      super(jedis.getClient());
      this.connection = connection;
      this.readMany = readMany;
      this.throwError = throwError;
      this.nestedErrors = nestedErrors;
    }

    @Override
    protected <T> Response<T> getResponse(Builder<T> builder) {
      return super.getResponse(
          new Builder<T>() {
            @Override
            public T build(Object response) {
              if (!read) {
                read = true;
                Throwable error = readNestedError(connection, readMany);
                nestedErrors.add(error);
                if (throwError) {
                  throw (JedisDataException) error;
                }
              }
              return builder.build(response);
            }
          });
    }
  }
}
