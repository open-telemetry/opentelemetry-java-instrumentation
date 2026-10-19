/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_SUMMARY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
      assertThat(client.query(QUERY)).isNotNull();
    }
    assertQuery("_OTHER");
  }

  @Test
  void asynchronousResponse() throws InterruptedException {
    CountDownLatch requestStarted = new CountDownLatch(1);
    CountDownLatch respond = new CountDownLatch(1);
    CountDownLatch callback = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try (InfluxDB client =
        client(
            chain -> {
              requestStarted.countDown();
              await(respond);
              return response(chain, FAILURE);
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
      assertQuery("_OTHER");
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
                            equalTo(ERROR_TYPE, errorType),
                            equalTo(
                                DB_RESPONSE_STATUS_CODE, "400".equals(errorType) ? "400" : null))));
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
                                    .hasAttributesSatisfying(
                                        attributes ->
                                            assertThat(attributes.asMap())
                                                .containsOnlyKeys(
                                                    Stream.of(
                                                            DB_SYSTEM_NAME,
                                                            DB_NAMESPACE,
                                                            DB_QUERY_SUMMARY,
                                                            SERVER_ADDRESS,
                                                            SERVER_PORT,
                                                            errorType == null ? null : ERROR_TYPE)
                                                        .filter(Objects::nonNull)
                                                        .toArray(AttributeKey<?>[]::new))))));
  }

  private static void assertNoDuration() {
    assertThat(testing.metrics())
        .filteredOn(metric -> metric.getName().equals("db.client.operation.duration"))
        .isEmpty();
  }
}
