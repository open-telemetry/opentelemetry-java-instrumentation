/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.JsonEncodingException;
import com.squareup.moshi.Moshi;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.InfluxDBIOException;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import retrofit2.Retrofit;

@SuppressWarnings("deprecation") // Query callbacks and QueryResult use the legacy client API.
class InfluxDbQueryLifecycleTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Query QUERY = new Query("SELECT * FROM cpu", "mydb");
  private static final String SUCCESS = "{\"results\":[{\"statement_id\":0}]}";

  @ParameterizedTest
  @ValueSource(strings = {"synchronous", "asynchronous", "chunked"})
  void nullQueryDoesNotAffectCallDepth(String overload) {
    try (InfluxDB client = client(chain -> response(chain, SUCCESS))) {
      assertThatThrownBy(
              () -> {
                switch (overload) {
                  case "synchronous":
                    client.query(null);
                    break;
                  case "asynchronous":
                    client.query(null, result -> {}, error -> {});
                    break;
                  case "chunked":
                    client.query(null, 1, result -> {});
                    break;
                  default:
                    throw new AssertionError(overload);
                }
              })
          .isInstanceOf(NullPointerException.class);
      assertThat(client.query(QUERY)).isNotNull();
    }
    assertQuery(null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"error\":\"private query text\"}",
        "{\"results\":[{}, {\"error\":\"private query text\"}]}"
      })
  void synchronousQueryResultError(String body) {
    try (InfluxDB client = client(chain -> response(chain, body))) {
      assertThat(client.query(QUERY)).isNotNull();
    }
    assertQuery("_OTHER");
  }

  @Test
  void doneMarkerIsNotAQueryError() {
    try (InfluxDB client = client(chain -> response(chain, "{\"error\":\"DONE\"}"))) {
      assertThat(client.query(QUERY)).isNotNull();
    }
    assertQuery(null);
  }

  @Test
  void synchronousException() {
    try (InfluxDB client =
        client(
            chain -> {
              throw new IOException("transport failure");
            })) {
      assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(InfluxDBIOException.class);
    }
    assertQuery(InfluxDBIOException.class.getName());
  }

  @Test
  void unrelatedInterceptorMoshiFailureIsNotRecorded() {
    AtomicReference<JsonDataException> caughtError = new AtomicReference<>();
    JsonAdapter<Integer> adapter = new Moshi.Builder().build().adapter(Integer.class);
    try (InfluxDB client =
        client(
            chain -> {
              try {
                assertThat(adapter.fromJson(new Buffer().writeUtf8("\"not an integer\"")))
                    .isNotNull();
              } catch (JsonDataException e) {
                caughtError.set(e);
              }
              return response(chain, SUCCESS);
            })) {
      assertThat(client.query(QUERY)).isNotNull();
    }
    assertThat(caughtError.get()).isInstanceOf(JsonDataException.class);
    assertQuery(null);
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasEventsSatisfyingExactly()));
  }

  @Test
  void reentrantAsyncQueryDoesNotEndSynchronousQuery() throws InterruptedException {
    CountDownLatch nestedRequestStarted = new CountDownLatch(1);
    CountDownLatch nestedCallback = new CountDownLatch(1);
    CountDownLatch nestedCallCompleted = new CountDownLatch(1);
    CountDownLatch releaseOuter = new CountDownLatch(1);
    ThreadPoolExecutor nestedExecutor =
        new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60,
            SECONDS,
            new SynchronousQueue<>(),
            Executors.defaultThreadFactory()) {
          @Override
          protected void afterExecute(Runnable runnable, Throwable error) {
            nestedCallCompleted.countDown();
          }
        };
    Dispatcher nestedDispatcher = new Dispatcher(nestedExecutor);
    OkHttpClient.Builder nestedClientBuilder =
        new OkHttpClient.Builder()
            .dispatcher(nestedDispatcher)
            .addInterceptor(
                chain -> {
                  nestedRequestStarted.countDown();
                  return response(chain, SUCCESS);
                });
    try (InfluxDB nestedClient =
            InfluxDBFactory.connect("http://localhost:8086", nestedClientBuilder);
        InfluxDB outerClient =
            client(
                chain -> {
                  nestedClient.query(
                      QUERY,
                      result -> nestedCallback.countDown(),
                      error -> nestedCallback.countDown());
                  await(releaseOuter);
                  throw new IOException("outer transport failure");
                })) {
      FutureTask<Void> outerQuery =
          new FutureTask<>(
              () -> {
                outerClient.query(QUERY);
                return null;
              });
      new Thread(outerQuery).start();
      try {
        assertThat(nestedRequestStarted.await(10, SECONDS)).isTrue();
        assertThat(nestedCallback.await(10, SECONDS)).isTrue();
        assertThat(nestedCallCompleted.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        releaseOuter.countDown();
        nestedExecutor.shutdownNow();
      }
      assertThatThrownBy(() -> outerQuery.get(10, SECONDS))
          .hasCauseInstanceOf(InfluxDBIOException.class);
      assertQuery(InfluxDBIOException.class.getName());
    }
  }

  @Test
  void sameClientReentrantEnqueueDoesNotEndOuterQuery() throws Exception {
    CountDownLatch outerBlocked = new CountDownLatch(1);
    CountDownLatch releaseOuter = new CountDownLatch(1);
    AtomicReference<InfluxDB> clientReference = new AtomicReference<>();
    AtomicBoolean enqueueingOuter = new AtomicBoolean(true);
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, SECONDS, new SynchronousQueue<>()) {
          @Override
          public void execute(Runnable command) {
            if (enqueueingOuter.compareAndSet(true, false)) {
              clientReference
                  .get()
                  .query(new Query("SELECT * FROM nested", "mydb"), result -> {}, error -> {});
              outerBlocked.countDown();
              await(releaseOuter);
              super.execute(command);
            } else {
              command.run();
            }
          }
        };
    try (InfluxDB client =
        InfluxDBFactory.connect(
            "http://localhost:8086",
            new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(executor))
                .addInterceptor(
                    chain -> {
                      if (chain.request().url().queryParameter("q").contains("nested")) {
                        return response(chain, SUCCESS);
                      }
                      throw new IOException("outer transport failure");
                    }))) {
      clientReference.set(client);
      FutureTask<Void> outerQuery =
          new FutureTask<>(
              () -> {
                client.query(QUERY, result -> {}, error -> {});
                return null;
              });
      new Thread(outerQuery).start();
      try {
        assertThat(outerBlocked.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        releaseOuter.countDown();
      }
      outerQuery.get(10, SECONDS);
      assertQuery(IOException.class.getName());
      assertException(IOException.class.getName());
    } finally {
      releaseOuter.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void sameClientReentrantQueryDuringCommandEncodingDoesNotEndOuterQuery() throws Exception {
    CountDownLatch nestedCallCompleted = new CountDownLatch(1);
    CountDownLatch outerBlocked = new CountDownLatch(1);
    CountDownLatch releaseOuter = new CountDownLatch(1);
    AtomicReference<InfluxDB> clientReference = new AtomicReference<>();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, SECONDS, new SynchronousQueue<>()) {
          @Override
          protected void afterExecute(Runnable runnable, Throwable error) {
            nestedCallCompleted.countDown();
          }
        };
    Dispatcher dispatcher = new Dispatcher(executor);
    Query outerQuery =
        new Query("SELECT * FROM cpu", "mydb") {
          @Override
          public String getCommandWithUrlEncoded() {
            clientReference
                .get()
                .query(new Query("SELECT * FROM nested", "mydb"), result -> {}, error -> {});
            await(nestedCallCompleted);
            outerBlocked.countDown();
            await(releaseOuter);
            return super.getCommandWithUrlEncoded();
          }
        };
    try (InfluxDB client =
        InfluxDBFactory.connect(
            "http://localhost:8086",
            new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .addInterceptor(
                    chain -> {
                      if (chain.request().url().queryParameter("q").contains("nested")) {
                        return response(chain, SUCCESS);
                      }
                      throw new IOException("outer transport failure");
                    }))) {
      clientReference.set(client);
      FutureTask<Void> outerQueryTask =
          new FutureTask<>(
              () -> {
                client.query(outerQuery, result -> {}, error -> {});
                return null;
              });
      new Thread(outerQueryTask).start();
      try {
        assertThat(outerBlocked.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        releaseOuter.countDown();
      }
      outerQueryTask.get(10, SECONDS);
      assertQuery(IOException.class.getName());
      assertException(IOException.class.getName());
    } finally {
      releaseOuter.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void propagatedAsyncContextDoesNotEndOuterQuery() throws InterruptedException {
    CountDownLatch nestedCallback = new CountDownLatch(1);
    CountDownLatch nestedCallCompleted = new CountDownLatch(1);
    CountDownLatch outerBlocked = new CountDownLatch(1);
    CountDownLatch releaseOuter = new CountDownLatch(1);
    ThreadPoolExecutor nestedExecutor =
        new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60,
            SECONDS,
            new SynchronousQueue<>(),
            Executors.defaultThreadFactory()) {
          @Override
          protected void afterExecute(Runnable runnable, Throwable error) {
            nestedCallCompleted.countDown();
          }
        };
    Dispatcher nestedDispatcher = new Dispatcher(nestedExecutor);
    ThreadPoolExecutor outerExecutor =
        new ThreadPoolExecutor(
            0,
            Integer.MAX_VALUE,
            60,
            SECONDS,
            new SynchronousQueue<>(),
            Executors.defaultThreadFactory()) {
          @Override
          public void execute(Runnable command) {
            super.execute(Context.current().wrap(command));
          }
        };
    Dispatcher outerDispatcher = new Dispatcher(outerExecutor);
    try (InfluxDB nestedClient =
            InfluxDBFactory.connect(
                "http://localhost:8086",
                new OkHttpClient.Builder()
                    .dispatcher(nestedDispatcher)
                    .addInterceptor(chain -> response(chain, SUCCESS)));
        InfluxDB outerClient =
            InfluxDBFactory.connect(
                "http://localhost:8086",
                new OkHttpClient.Builder()
                    .dispatcher(outerDispatcher)
                    .addInterceptor(
                        chain -> {
                          nestedClient.query(
                              QUERY,
                              result -> nestedCallback.countDown(),
                              error -> nestedCallback.countDown());
                          await(nestedCallCompleted);
                          outerBlocked.countDown();
                          await(releaseOuter);
                          throw new IOException("outer transport failure");
                        }))) {
      outerClient.query(QUERY, result -> {}, error -> {});
      try {
        assertThat(outerBlocked.await(10, SECONDS)).isTrue();
        assertThat(nestedCallback.getCount()).isZero();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
        releaseOuter.countDown();
        assertQuery(IOException.class.getName());
      } finally {
        releaseOuter.countDown();
        nestedExecutor.shutdownNow();
        outerExecutor.shutdownNow();
      }
    }
  }

  @ParameterizedTest
  @MethodSource("asynchronousResponses")
  void asynchronousResponse(boolean hasFailureConsumer, boolean callbackThrows, String errorType)
      throws InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch respond = new CountDownLatch(1);
    CountDownLatch callback = new CountDownLatch(1);
    CountDownLatch complete = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain -> {
              requestStarted.countDown();
              await(respond);
              return response(chain, SUCCESS);
            })) {
      Consumer<Throwable> failureConsumer =
          error -> {
            failure.set(error);
            callback.countDown();
          };
      client.query(
          QUERY,
          result -> {
            callback.countDown();
            await(complete);
            if (callbackThrows) {
              throw new IllegalStateException("private callback text");
            }
          },
          hasFailureConsumer ? failureConsumer : null);
      try {
        assertThat(requestStarted.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
        respond.countDown();
        assertThat(callback.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        respond.countDown();
        complete.countDown();
      }
      assertThat(failure.get()).isNull();
      assertQuery(errorType);
    }
  }

  private static Stream<Arguments> asynchronousResponses() {
    return Stream.of(
        argumentSet("success", true, false, null),
        argumentSet("success without failure consumer", false, false, null),
        argumentSet("callback error", true, true, IllegalStateException.class.getName()),
        argumentSet(
            "callback error without failure consumer",
            false,
            true,
            IllegalStateException.class.getName()));
  }

  @Test
  void asynchronousQueryResultError() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    AtomicReference<QueryResult> receivedResult = new AtomicReference<>();
    try (InfluxDB client =
        client(chain -> response(chain, "{\"error\":\"private query error\"}"))) {
      client.query(
          QUERY,
          result -> {
            receivedResult.set(result);
            callback.countDown();
          },
          error -> {});
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertThat(receivedResult.get()).isNotNull();
      assertThat(receivedResult.get().getError()).isEqualTo("private query error");
      assertQuery("_OTHER");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"consumer", "bi-consumer"})
  void chunkedQueryResultError(String callbackType) throws InterruptedException {
    CountDownLatch callbacks = new CountDownLatch(3);
    AtomicInteger callbackCount = new AtomicInteger();
    AtomicReference<QueryResult> firstChunk = new AtomicReference<>();
    AtomicReference<QueryResult> errorChunk = new AtomicReference<>();
    AtomicReference<QueryResult> lastChunk = new AtomicReference<>();
    AtomicReference<QueryResult> doneMarker = new AtomicReference<>();
    Consumer<QueryResult> callback =
        result -> {
          switch (callbackCount.getAndIncrement()) {
            case 0:
              firstChunk.set(result);
              break;
            case 1:
              errorChunk.set(result);
              break;
            case 2:
              lastChunk.set(result);
              break;
            default:
              if ("DONE".equals(result.getError())) {
                doneMarker.set(result);
              } else {
                throw new AssertionError("unexpected query result chunk");
              }
          }
          callbacks.countDown();
        };
    String body =
        SUCCESS
            + "\n"
            + "{\"results\":[{\"statement_id\":0,\"error\":\"private statement error\"}]}\n"
            + SUCCESS
            + "\n";
    try (InfluxDB client = client(chain -> response(chain, body))) {
      if ("consumer".equals(callbackType)) {
        client.query(QUERY, 1, callback);
      } else {
        client.query(QUERY, 1, (cancellable, result) -> callback.accept(result));
      }
      assertThat(callbacks.await(10, SECONDS)).isTrue();
      assertThat(firstChunk.get().getResults().get(0).getError()).isNull();
      assertThat(errorChunk.get().getResults().get(0).getError())
          .isEqualTo("private statement error");
      assertThat(lastChunk.get().getResults().get(0).getError()).isNull();
      assertQuery("_OTHER");
      assertThat(callbackCount.get()).isBetween(3, 4);
      if (doneMarker.get() != null) {
        assertThat(doneMarker.get().getError()).isEqualTo("DONE");
      }
    }
  }

  @Test
  void asynchronousHttpErrorResponse() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    AtomicBoolean successCallbackCalled = new AtomicBoolean();
    AtomicReference<QueryResult> successResult = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain ->
                response(chain, "{\"error\":\"private HTTP error\"}")
                    .newBuilder()
                    .code(400)
                    .message("Bad Request")
                    .build())) {
      client.query(
          QUERY,
          result -> {
            successCallbackCalled.set(true);
            successResult.set(result);
            callback.countDown();
          },
          error -> {
            failure.set(error);
            callback.countDown();
          });
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertThat(successResult.get()).isNull();
      if (testLatestDeps() || "2.25".equals(System.getProperty("influxdb.test.client-version"))) {
        assertThat(successCallbackCalled.get()).isFalse();
        assertThat(failure.get()).isInstanceOf(InfluxDBException.class);
        assertQuery(InfluxDBException.class.getName());
      } else {
        assertThat(successCallbackCalled.get()).isTrue();
        assertThat(failure.get()).isNull();
        assertQuery(null);
      }
    }
  }

  @Test
  void chunkedResponseFinishesAfterCompletion() throws InterruptedException {
    CountDownLatch firstChunk = new CountDownLatch(1);
    CountDownLatch nextChunk = new CountDownLatch(1);
    CountDownLatch completion = new CountDownLatch(1);
    CountDownLatch complete = new CountDownLatch(1);
    AtomicReference<String> callbackParent = new AtomicReference<>();
    AtomicReference<String> parent = new AtomicReference<>();
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n" + SUCCESS + "\n"))) {
      testing.runWithSpan(
          "parent",
          () -> {
            parent.set(Span.current().getSpanContext().getSpanId());
            client.query(
                QUERY,
                1,
                (cancellable, result) -> {
                  callbackParent.set(Span.current().getSpanContext().getSpanId());
                  if (firstChunk.getCount() != 0) {
                    firstChunk.countDown();
                    await(nextChunk);
                  }
                },
                () -> {
                  completion.countDown();
                  await(complete);
                },
                error -> completion.countDown());
          });
      try {
        assertThat(firstChunk.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).hasSize(1);
        assertNoDuration();
        nextChunk.countDown();
        assertThat(completion.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).hasSize(1);
        assertNoDuration();
        assertThat(callbackParent.get()).isEqualTo(parent.get());
      } finally {
        nextChunk.countDown();
        complete.countDown();
      }
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      span.hasName("SELECT cpu")
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.unset())
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, "influxdb"),
                              equalTo(DB_NAMESPACE, "mydb"),
                              equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                              equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, 8086))));
      assertMetric(null);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "consumer",
        "bi-consumer",
        "consumer-completion",
        "bi-consumer-completion",
        "bi-consumer-completion-error"
      })
  void chunkedOverloadsPreserveParentContext(String overload) throws InterruptedException {
    CountDownLatch callbackStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<String> parent = new AtomicReference<>();
    AtomicReference<String> callbackParent = new AtomicReference<>();
    AtomicReference<String> completionParent = new AtomicReference<>();
    Runnable completion = () -> completionParent.set(Span.current().getSpanContext().getSpanId());
    Consumer<QueryResult> callback =
        result -> {
          callbackParent.set(Span.current().getSpanContext().getSpanId());
          callbackStarted.countDown();
          await(release);
        };
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n"))) {
      testing.runWithSpan(
          "parent",
          () -> {
            parent.set(Span.current().getSpanContext().getSpanId());
            switch (overload) {
              case "consumer":
                client.query(QUERY, 1, callback);
                break;
              case "bi-consumer":
                client.query(QUERY, 1, (cancellable, result) -> callback.accept(result));
                break;
              case "consumer-completion":
                client.query(QUERY, 1, callback, completion);
                break;
              case "bi-consumer-completion":
                client.query(
                    QUERY, 1, (cancellable, result) -> callback.accept(result), completion);
                break;
              case "bi-consumer-completion-error":
                client.query(
                    QUERY,
                    1,
                    (cancellable, result) -> callback.accept(result),
                    completion,
                    error -> {});
                break;
              default:
                throw new AssertionError(overload);
            }
            assertThat(Span.current().getSpanContext().getSpanId()).isEqualTo(parent.get());
          });
      try {
        assertThat(callbackStarted.await(10, SECONDS)).isTrue();
        assertThat(callbackParent.get()).isEqualTo(parent.get());
        assertThat(testing.spans()).hasSize(1);
        assertNoDuration();
      } finally {
        release.countDown();
      }
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      span.hasName("SELECT cpu")
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.unset())
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, "influxdb"),
                              equalTo(DB_NAMESPACE, "mydb"),
                              equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                              equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, 8086))));
      assertThat(completionParent.get())
          .isEqualTo(overload.contains("completion") ? parent.get() : null);
      assertMetric(null);
    }
  }

  @Test
  void asynchronousFailurePreservesParentContext() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (InfluxDB client =
        client(
            chain -> {
              throw new IOException("transport failure");
            })) {
      testing.runWithSpan(
          "parent",
          () ->
              client.query(
                  QUERY,
                  result -> {},
                  error -> {
                    testing.runWithSpan("callback", () -> {});
                    callback.countDown();
                    await(release);
                  }));
      try {
        assertThat(callback.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).hasSize(2);
        assertNoDuration();
      } finally {
        release.countDown();
      }
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      span.hasName("SELECT cpu")
                          .hasParent(trace.getSpan(0))
                          .hasStatus(StatusData.error())
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, "influxdb"),
                              equalTo(DB_NAMESPACE, "mydb"),
                              equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                              equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, 8086),
                              equalTo(ERROR_TYPE, IOException.class.getName())),
                  span -> span.hasName("callback").hasParent(trace.getSpan(0))));
      assertMetric(IOException.class.getName());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"asynchronous", "chunked", "completion", "failure"})
  void callbackException(String callback) {
    try (InfluxDB client =
        client(
            chain -> {
              if (callback.equals("failure")) {
                throw new IOException("transport failure");
              }
              return response(chain, SUCCESS + "\n");
            })) {
      switch (callback) {
        case "asynchronous":
          client.query(
              QUERY,
              result -> {
                throw new IllegalStateException("private callback text");
              },
              error -> {});
          break;
        case "chunked":
          client.query(
              QUERY,
              1,
              result -> {
                throw new IllegalStateException("private callback text");
              });
          break;
        case "completion":
          client.query(
              QUERY,
              1,
              result -> {},
              () -> {
                throw new IllegalStateException("private callback text");
              });
          break;
        case "failure":
          client.query(
              QUERY,
              result -> {},
              error -> {
                throw new IllegalStateException("private callback text");
              });
          break;
        default:
          throw new AssertionError(callback);
      }
      assertQuery(IllegalStateException.class.getName());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void canceledCallWithDecodingFailure(boolean hasFailureConsumer) throws InterruptedException {
    CountDownLatch readStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch failureCallback = new CountDownLatch(1);
    AtomicReference<Throwable> receivedFailure = new AtomicReference<>();
    BufferedSource source =
        Okio.buffer(
            new ForwardingSource(new Buffer()) {
              private boolean read;

              @Override
              public long read(Buffer sink, long byteCount) {
                if (read) {
                  return -1;
                }
                read = true;
                readStarted.countDown();
                await(release);
                return sink.writeUtf8("{\"results\":{}}").size();
              }
            });
    Dispatcher dispatcher = new Dispatcher();
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(
                chain ->
                    response(chain, SUCCESS)
                        .newBuilder()
                        .body(ResponseBody.create(MediaType.parse("application/json"), -1, source))
                        .build());
    try (InfluxDB client = InfluxDBFactory.connect("http://localhost:8086", builder)) {
      Consumer<Throwable> failure =
          error -> {
            receivedFailure.set(error);
            failureCallback.countDown();
          };
      client.query(QUERY, result -> {}, hasFailureConsumer ? failure : null);
      try {
        assertThat(readStarted.await(10, SECONDS)).isTrue();
        dispatcher.cancelAll();
      } finally {
        release.countDown();
      }
      if (hasFailureConsumer) {
        assertThat(failureCallback.await(10, SECONDS)).isTrue();
        assertThat(receivedFailure.get()).isInstanceOf(JsonDataException.class);
      }
      assertQuery("com.squareup.moshi.JsonDataException");
    }
  }

  @Test
  void canceledCallWithJsonEncodingFailure() throws InterruptedException {
    CountDownLatch readStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch failureCallback = new CountDownLatch(1);
    AtomicReference<Throwable> receivedFailure = new AtomicReference<>();
    BufferedSource source =
        Okio.buffer(
            new ForwardingSource(new Buffer()) {
              private boolean read;

              @Override
              public long read(Buffer sink, long byteCount) {
                if (read) {
                  return -1;
                }
                read = true;
                readStarted.countDown();
                await(release);
                return sink.writeUtf8("{\"results\":@}").size();
              }
            });
    Dispatcher dispatcher = new Dispatcher();
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(
                chain ->
                    response(chain, SUCCESS)
                        .newBuilder()
                        .body(ResponseBody.create(MediaType.parse("application/json"), -1, source))
                        .build());
    try (InfluxDB client = InfluxDBFactory.connect("http://localhost:8086", builder)) {
      client.query(
          QUERY,
          result -> {},
          error -> {
            receivedFailure.set(error);
            failureCallback.countDown();
          });
      try {
        assertThat(readStarted.await(10, SECONDS)).isTrue();
        dispatcher.cancelAll();
      } finally {
        release.countDown();
      }
      assertThat(failureCallback.await(10, SECONDS)).isTrue();
      assertThat(receivedFailure.get()).isInstanceOf(JsonEncodingException.class);
      assertQuery(JsonEncodingException.class.getName());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectedCallbackExecutorCompletesQuery(boolean failTransport) throws InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch releaseRequest = new CountDownLatch(1);
    CountDownLatch rejected = new CountDownLatch(1);
    AtomicBoolean callbackInvoked = new AtomicBoolean();
    Executor callbackExecutor =
        command -> {
          rejected.countDown();
          throw new RejectedExecutionException("callback executor rejected dispatch");
        };
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain -> {
                  requestStarted.countDown();
                  await(releaseRequest);
                  if (failTransport) {
                    throw new IOException("transport failure");
                  }
                  return response(chain, SUCCESS);
                });
    try (InfluxDB client =
        new InfluxDBImpl(
            "http://localhost:8086",
            null,
            null,
            builder,
            new Retrofit.Builder().callbackExecutor(callbackExecutor),
            InfluxDB.ResponseFormat.JSON)) {
      client.query(QUERY, result -> callbackInvoked.set(true), error -> callbackInvoked.set(true));
      try {
        assertThat(requestStarted.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
        releaseRequest.countDown();
        assertThat(rejected.await(10, SECONDS)).isTrue();
        assertQuery(RejectedExecutionException.class.getName());
        assertException(RejectedExecutionException.class.getName());
        assertThat(callbackInvoked.get()).isFalse();
      } finally {
        releaseRequest.countDown();
      }
    }
  }

  @Test
  void cancellationIsNotFailure() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n" + SUCCESS + "\n"))) {
      client.query(
          QUERY,
          1,
          (cancellable, result) -> {
            cancellable.cancel();
            callback.countDown();
          },
          () -> {},
          error -> {});
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertQuery(null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationBeforeResponse(boolean failureConsumerThrows) throws InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Dispatcher dispatcher = new Dispatcher();
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(
                chain -> {
                  requestStarted.countDown();
                  await(release);
                  throw new IOException("Canceled");
                });
    try (InfluxDB client = InfluxDBFactory.connect("http://localhost:8086", builder)) {
      if (failureConsumerThrows) {
        client.query(
            QUERY,
            1,
            (cancellable, result) -> {},
            () -> {},
            error -> {
              throw new IllegalStateException("private callback text");
            });
      } else {
        client.query(QUERY, 1, result -> {});
      }
      try {
        assertThat(requestStarted.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
        dispatcher.cancelAll();
      } finally {
        release.countDown();
      }
      assertQuery(failureConsumerThrows ? IllegalStateException.class.getName() : null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void callbackFailureAfterCancellation(boolean wrappedReadFailure) throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    Dispatcher dispatcher = new Dispatcher();
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(chain -> response(chain, SUCCESS));
    try (InfluxDB client = InfluxDBFactory.connect("http://localhost:8086", builder)) {
      client.query(
          QUERY,
          result -> {
            dispatcher.cancelAll();
            callback.countDown();
            if (wrappedReadFailure) {
              throw new InfluxDBException(new IOException("private callback text"));
            }
            throw new IllegalStateException("private callback text");
          },
          error -> {});
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertQuery(
          wrappedReadFailure
              ? InfluxDBException.class.getName()
              : IllegalStateException.class.getName());
    }
  }

  private static InfluxDB client(Interceptor interceptor) {
    return InfluxDBFactory.connect(
        "http://localhost:8086", new OkHttpClient.Builder().addInterceptor(interceptor));
  }

  private static Response response(Interceptor.Chain chain, String body) {
    return new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(MediaType.parse("application/json"), body))
        .build();
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private static void assertQuery(String errorType) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT cpu")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(errorType == null ? StatusData.unset() : StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "influxdb"),
                            equalTo(DB_NAMESPACE, "mydb"),
                            equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                            equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, 8086),
                            equalTo(ERROR_TYPE, errorType))));
    assertMetric(errorType);
  }

  private static void assertMetric(String errorType) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.influxdb-2.4",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasUnit("s")
                .hasHistogramSatisfying(
                    histogram -> {
                      histogram.satisfies(data -> assertThat(data.getPoints()).hasSize(1));
                      histogram.hasPointsSatisfying(
                          point ->
                              point
                                  .hasCount(1)
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(DB_SYSTEM_NAME, "influxdb"),
                                      equalTo(DB_NAMESPACE, "mydb"),
                                      equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, 8086),
                                      equalTo(ERROR_TYPE, errorType)));
                    }));
  }

  private static void assertNoDuration() {
    assertThat(testing.metrics())
        .filteredOn(metric -> metric.getName().equals("db.client.operation.duration"))
        .isEmpty();
  }

  private static void assertException(String errorType) {
    if (errorType == null) {
      assertThat(testing.spans().get(0).getEvents()).isEmpty();
    } else {
      assertThat(testing.spans().get(0).getEvents())
          .extracting(event -> event.getAttributes().get(EXCEPTION_TYPE))
          .containsExactly(errorType);
    }
  }
}
