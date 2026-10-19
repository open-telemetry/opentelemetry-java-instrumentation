/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_SUMMARY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

@SuppressWarnings("deprecation") // Query callbacks and QueryResult use the legacy client API.
class InfluxDbQueryLifecycleTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Query QUERY = new Query("SELECT * FROM cpu", "mydb");
  private static final String SUCCESS = "{\"results\":[{\"statement_id\":0}]}";
  private static final String FAILURE = "{\"results\":[{}, {\"error\":\"private query text\"}]}";

  @ParameterizedTest
  @ValueSource(strings = {"{\"error\":\"private query text\"}", FAILURE})
  void synchronousResponseFailure(String body) {
    try (InfluxDB client = client(chain -> response(chain, body))) {
      QueryResult result = client.query(QUERY);
      assertThat(result).isNotNull();
    }
    assertQuery("_OTHER");
  }

  @Test
  void synchronousTransportFailure() {
    try (InfluxDB client =
        client(
            chain -> {
              throw new IOException("private query text");
            })) {
      assertThatThrownBy(() -> client.query(QUERY)).isInstanceOf(RuntimeException.class);
    }
    assertQuery("org.influxdb.InfluxDBIOException");
  }

  @Test
  void synchronousSuccess() {
    try (InfluxDB client = client(chain -> response(chain, SUCCESS))) {
      assertThat(client.query(QUERY).hasError()).isFalse();
    }
    assertQuery(null);
  }

  @ParameterizedTest
  @ValueSource(strings = {SUCCESS, "{\"error\":\"private query text\"}", FAILURE})
  void asynchronousResponse(String body) throws InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch respond = new CountDownLatch(1);
    CountDownLatch callback = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain -> {
              requestStarted.countDown();
              await(respond);
              return response(chain, body);
            })) {
      client.query(
          QUERY,
          result -> callback.countDown(),
          error -> {
            failure.set(error);
            callback.countDown();
          });
      try {
        assertThat(requestStarted.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        respond.countDown();
      }
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertThat(failure.get()).isNull();
      assertQuery(body.equals(SUCCESS) ? null : "_OTHER");
    }
  }

  @Test
  void asynchronousTransportFailure() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain -> {
              throw new IOException("private query text");
            })) {
      client.query(
          QUERY,
          result -> callback.countDown(),
          error -> {
            failure.set(error);
            callback.countDown();
          });
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertThat(failure.get()).isInstanceOf(IOException.class);
      assertQuery(IOException.class.getName());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"response", "chunk", "complete"})
  void callbackFailureFinishesOnce(String callbackType) throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    Runnable fail =
        () -> {
          callback.countDown();
          throw new IllegalStateException("private callback text");
        };
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n"))) {
      if (callbackType.equals("response")) {
        client.query(QUERY, result -> fail.run(), error -> {});
      } else {
        client.query(
            QUERY,
            1,
            (cancellable, result) -> {
              if (callbackType.equals("chunk")) {
                fail.run();
              }
            },
            () -> {
              if (callbackType.equals("complete")) {
                fail.run();
              }
            },
            error -> {});
      }
      assertThat(callback.await(10, SECONDS)).isTrue();
      assertQuery(IllegalStateException.class.getName());
    }
  }

  @Test
  void successfulQueryAfterFailure() {
    AtomicInteger requests = new AtomicInteger();
    try (InfluxDB client =
        client(chain -> response(chain, requests.getAndIncrement() == 0 ? FAILURE : SUCCESS))) {
      assertThat(client.query(QUERY)).isNotNull();
      assertQuery("_OTHER");
      testing.clearData();
      assertThat(client.query(QUERY).hasError()).isFalse();
      assertQuery(null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void chunkedResponseFinishesAfterCompletion(boolean failed) throws InterruptedException {
    CountDownLatch firstChunk = new CountDownLatch(1);
    CountDownLatch nextChunk = new CountDownLatch(1);
    CountDownLatch completion = new CountDownLatch(1);
    CountDownLatch complete = new CountDownLatch(1);
    AtomicReference<String> callbackParent = new AtomicReference<>();
    AtomicReference<String> parent = new AtomicReference<>();
    try (InfluxDB client =
        client(chain -> response(chain, SUCCESS + "\n" + (failed ? FAILURE : SUCCESS) + "\n"))) {
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
                          .hasStatus(failed ? StatusData.error() : StatusData.unset())
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, "influxdb"),
                              equalTo(DB_NAMESPACE, "mydb"),
                              equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                              equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, 8086),
                              equalTo(ERROR_TYPE, failed ? "_OTHER" : null))));
      assertMetric(failed ? "_OTHER" : null);
    }
  }

  @Test
  void cancellationIsNotFailure() throws InterruptedException {
    CountDownLatch callback = new CountDownLatch(1);
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n" + FAILURE + "\n"))) {
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
  void cancellationDuringChunkRead(boolean priorFailure) throws InterruptedException {
    CountDownLatch reading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<InfluxDB.Cancellable> cancellable = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain ->
                response(chain, "")
                    .newBuilder()
                    .body(
                        new ResponseBody() {
                          private final BufferedSource source =
                              Okio.buffer(
                                  new ForwardingSource(
                                      new Buffer()
                                          .writeUtf8((priorFailure ? FAILURE : SUCCESS) + "\n")) {
                                    @Override
                                    public long read(Buffer sink, long byteCount)
                                        throws IOException {
                                      long count = super.read(sink, byteCount);
                                      if (count != -1) {
                                        return count;
                                      }
                                      reading.countDown();
                                      await(release);
                                      throw new IOException("Canceled");
                                    }
                                  });

                          @Override
                          public MediaType contentType() {
                            return MediaType.parse("application/json");
                          }

                          @Override
                          public long contentLength() {
                            return -1;
                          }

                          @Override
                          public BufferedSource source() {
                            return source;
                          }
                        })
                    .build())) {
      client.query(QUERY, 1, (handle, result) -> cancellable.set(handle), () -> {}, error -> {});
      try {
        assertThat(reading.await(10, SECONDS)).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
        cancellable.get().cancel();
      } finally {
        release.countDown();
      }
      assertQuery(priorFailure ? "_OTHER" : null);
    }
  }

  @Test
  void chunkedOverloadWithoutCompletionCallback() throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    try (InfluxDB client = client(chain -> response(chain, SUCCESS + "\n"))) {
      client.query(
          QUERY,
          1,
          result -> {
            if ("DONE".equals(result.getError())) {
              done.countDown();
            }
          });
      assertThat(done.await(10, SECONDS)).isTrue();
      assertQuery(null);
    }
  }

  @Test
  void messagePackChunksWithoutCompletionCallback() throws IOException, InterruptedException {
    byte[] body;
    try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
      packer
          .packMapHeader(1)
          .packString("results")
          .packArrayHeader(1)
          .packMapHeader(1)
          .packString("statement_id")
          .packInt(0);
      packer
          .packMapHeader(1)
          .packString("results")
          .packArrayHeader(1)
          .packMapHeader(1)
          .packString("statement_id")
          .packInt(1);
      body = packer.toByteArray();
    }
    CountDownLatch chunks = new CountDownLatch(2);
    try (InfluxDB client =
        InfluxDBFactory.connect(
            "http://localhost:8086",
            "user",
            "password",
            new OkHttpClient.Builder()
                .addInterceptor(
                    chain ->
                        response(chain, "")
                            .newBuilder()
                            .body(
                                ResponseBody.create(MediaType.parse("application/x-msgpack"), body))
                            .build()),
            InfluxDB.ResponseFormat.MSGPACK)) {
      client.query(QUERY, 1, result -> chunks.countDown());
      assertThat(chunks.await(10, SECONDS)).isTrue();
      assertQuery(null);
    }
  }

  @Test
  void chunkedHttpFailure() throws InterruptedException {
    CountDownLatch failed = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain ->
                response(chain, "{\"error\":\"private query text\"}")
                    .newBuilder()
                    .code(400)
                    .message("Bad Request")
                    .build())) {
      client.query(
          QUERY,
          1,
          (cancellable, result) -> {},
          () -> {},
          error -> {
            failure.set(error);
            failed.countDown();
          });
      assertThat(failed.await(10, SECONDS)).isTrue();
      assertThat(failure.get()).isInstanceOf(InfluxDBException.class);
      assertQuery("400");
    }
  }

  @Test
  void chunkedTransportFailure() throws InterruptedException {
    CountDownLatch failed = new CountDownLatch(1);
    try (InfluxDB client =
        client(
            chain -> {
              throw new IOException("private query text");
            })) {
      client.query(QUERY, 1, (cancellable, result) -> {}, () -> {}, error -> failed.countDown());
      assertThat(failed.await(10, SECONDS)).isTrue();
      assertQuery(IOException.class.getName());
    }
  }

  @Test
  void chunkDecodingFailure() {
    try (InfluxDB client = client(chain -> response(chain, "{\"results\":\"invalid\"}\n"))) {
      client.query(QUERY, 1, (cancellable, result) -> {}, () -> {}, error -> {});
      assertQuery("com.squareup.moshi.JsonDataException");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void chunkReadFailureFinishesOnce(boolean failureCallback) throws InterruptedException {
    CountDownLatch failed = new CountDownLatch(1);
    CountDownLatch leaveCallback = new CountDownLatch(1);
    CountDownLatch errorChunk = new CountDownLatch(1);
    try (InfluxDB client =
        client(
            chain ->
                response(chain, "")
                    .newBuilder()
                    .body(
                        new ResponseBody() {
                          private final BufferedSource source =
                              Okio.buffer(
                                  new ForwardingSource(new Buffer()) {
                                    @Override
                                    public long read(Buffer sink, long byteCount)
                                        throws IOException {
                                      throw new IOException("private query text");
                                    }
                                  });

                          @Override
                          public MediaType contentType() {
                            return MediaType.parse("application/json");
                          }

                          @Override
                          public long contentLength() {
                            return -1;
                          }

                          @Override
                          public BufferedSource source() {
                            return source;
                          }
                        })
                    .build())) {
      if (failureCallback) {
        client.query(
            QUERY,
            1,
            (cancellable, result) -> errorChunk.countDown(),
            () -> {},
            error -> {
              failed.countDown();
              await(leaveCallback);
            });
      } else {
        client.query(
            QUERY,
            1,
            result -> {
              errorChunk.countDown();
              failed.countDown();
              await(leaveCallback);
            });
      }
      try {
        assertThat(failed.await(10, SECONDS)).isTrue();
        assertThat(errorChunk.getCount()).isZero();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        leaveCallback.countDown();
      }
      assertQuery("_OTHER");
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
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .satisfies(data -> assertThat(data.getSum()).isPositive())
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(DB_SYSTEM_NAME, "influxdb"),
                                        equalTo(DB_NAMESPACE, "mydb"),
                                        equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                                        equalTo(SERVER_ADDRESS, "localhost"),
                                        equalTo(SERVER_PORT, 8086),
                                        equalTo(ERROR_TYPE, errorType)))));
  }

  private static void assertNoDuration() {
    assertThat(testing.metrics())
        .filteredOn(metric -> metric.getName().equals("db.client.operation.duration"))
        .isEmpty();
  }
}
