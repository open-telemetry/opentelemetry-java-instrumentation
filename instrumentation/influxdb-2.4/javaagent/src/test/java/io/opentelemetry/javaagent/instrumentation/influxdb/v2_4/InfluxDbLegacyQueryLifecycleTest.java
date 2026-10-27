/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Dispatcher;
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

@SuppressWarnings("deprecation") // Query callbacks and QueryResult use the legacy client API.
class InfluxDbLegacyQueryLifecycleTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String SUCCESS = "{\"results\":[{\"statement_id\":0}]}";

  @Test
  void sameQueryReentrantAsyncQueryDuringChunkedEncodingDoesNotEndOuterQuery() throws Exception {
    CountDownLatch nestedCallback = new CountDownLatch(1);
    CountDownLatch nestedCallbackCompleted = new CountDownLatch(1);
    CountDownLatch callbacksCompleted = new CountDownLatch(2);
    CountDownLatch outerBlocked = new CountDownLatch(1);
    CountDownLatch releaseOuter = new CountDownLatch(1);
    AtomicReference<InfluxDB> clientReference = new AtomicReference<>();
    AtomicReference<Thread> nestedCallbackThread = new AtomicReference<>();
    AtomicBoolean reenter = new AtomicBoolean(true);
    AtomicBoolean nestedSucceeded = new AtomicBoolean();
    AtomicInteger queryRequests = new AtomicInteger();
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, SECONDS, new SynchronousQueue<>()) {
          @Override
          protected void afterExecute(Runnable runnable, Throwable error) {
            if (Thread.currentThread() == nestedCallbackThread.get()) {
              nestedCallbackCompleted.countDown();
            }
            callbacksCompleted.countDown();
          }
        };
    Query query =
        new Query("SELECT * FROM cpu", "mydb") {
          @Override
          public String getCommandWithUrlEncoded() {
            if (reenter.compareAndSet(true, false)) {
              clientReference
                  .get()
                  .query(
                      this,
                      result -> {
                        nestedCallbackThread.set(Thread.currentThread());
                        nestedSucceeded.set(true);
                        nestedCallback.countDown();
                      },
                      error -> {
                        nestedCallbackThread.set(Thread.currentThread());
                        nestedCallback.countDown();
                      });
              await(nestedCallback);
              await(nestedCallbackCompleted);
              outerBlocked.countDown();
              await(releaseOuter);
            }
            return super.getCommandWithUrlEncoded();
          }
        };
    try (InfluxDB client =
        InfluxDBFactory.connect(
            "http://localhost:8086",
            new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(executor))
                .addInterceptor(
                    chain -> {
                      if (!chain.request().url().encodedPath().endsWith("/query")) {
                        return response(chain);
                      }
                      if (queryRequests.getAndIncrement() == 0) {
                        return response(chain);
                      }
                      throw new IOException("outer transport failure");
                    }))) {
      clientReference.set(client);
      FutureTask<Void> outerQuery =
          new FutureTask<>(
              () -> {
                client.query(query, 1, result -> {});
                return null;
              });
      new Thread(outerQuery).start();
      try {
        assertThat(outerBlocked.await(10, SECONDS)).isTrue();
        assertThat(nestedSucceeded.get()).isTrue();
        assertThat(testing.spans()).isEmpty();
        assertNoDuration();
      } finally {
        releaseOuter.countDown();
      }
      outerQuery.get(10, SECONDS);
      assertThat(callbacksCompleted.await(10, SECONDS)).isTrue();
    } finally {
      releaseOuter.countDown();
      executor.shutdownNow();
    }
    assertQuery(IOException.class.getName());
    assertException(IOException.class.getName());
  }

  private static Response response(Interceptor.Chain chain) {
    return new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(MediaType.parse("application/json"), SUCCESS))
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
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "influxdb"),
                            equalTo(DB_NAMESPACE, "mydb"),
                            equalTo(DB_QUERY_TEXT, "SELECT * FROM cpu"),
                            equalTo(DB_QUERY_SUMMARY, "SELECT cpu"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, 8086),
                            equalTo(ERROR_TYPE, errorType))));
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

  private static void assertException(String errorType) {
    assertThat(testing.spans().get(0).getEvents())
        .extracting(event -> event.getAttributes().get(EXCEPTION_TYPE))
        .containsExactly(errorType);
  }
}
