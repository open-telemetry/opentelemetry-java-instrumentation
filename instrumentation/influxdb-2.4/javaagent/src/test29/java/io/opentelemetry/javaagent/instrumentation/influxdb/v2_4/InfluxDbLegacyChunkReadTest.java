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
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
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
import okio.Source;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
class InfluxDbLegacyChunkReadTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Query QUERY = new Query("SELECT * FROM cpu", "mydb");

  @Test
  void readFailureIsRecordedBeforeClientSwallowsIt() {
    InfluxDB client =
        client(
            new ForwardingSource(new Buffer()) {
              @Override
              public long read(Buffer sink, long byteCount) throws IOException {
                throw new IOException("chunk read failure");
              }
            });
    try {
      client.query(QUERY, 1, result -> {});
      assertTelemetry(IOException.class.getName());
    } finally {
      client.close();
    }
  }

  @Test
  void endOfStreamIsNotRecordedAsFailure() {
    InfluxDB client =
        client(
            new ForwardingSource(new Buffer()) {
              @Override
              public long read(Buffer sink, long byteCount) throws IOException {
                throw new EOFException();
              }
            });
    try {
      client.query(QUERY, 1, result -> {});
      assertTelemetry(null);
    } finally {
      client.close();
    }
  }

  @Test
  void whitespaceOnlyEndOfStreamIsNotRecordedAsFailure() {
    InfluxDB client = client(new Buffer().writeUtf8(" \n\t"));
    try {
      client.query(QUERY, 1, result -> {});
      assertTelemetry(null);
    } finally {
      client.close();
    }
  }

  @Test
  void truncatedJsonIsRecordedBeforeClientSwallowsIt() {
    InfluxDB client = client(new Buffer().writeUtf8("{\"results\":["));
    try {
      client.query(QUERY, 1, result -> {});
      assertTelemetry(EOFException.class.getName());
    } finally {
      client.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"results\":[", "{\"results\":[{\"error\":\"\\u12", "", " \n\t"})
  void cancellationRetainsOnlyIncompleteValueFailure(String json) throws InterruptedException {
    CountDownLatch readStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Dispatcher dispatcher = new Dispatcher();
    Source source =
        new ForwardingSource(new Buffer().writeUtf8(json)) {
          @Override
          public long read(Buffer sink, long byteCount) throws IOException {
            readStarted.countDown();
            try {
              if (!release.await(10, SECONDS)) {
                throw new IOException("release timed out");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IOException(e);
            }
            return super.read(sink, byteCount);
          }
        };
    InfluxDB client = client(source, dispatcher);
    try {
      client.query(QUERY, 1, result -> {});
      try {
        assertThat(readStarted.await(10, SECONDS)).isTrue();
        dispatcher.cancelAll();
      } finally {
        release.countDown();
      }
      assertTelemetry(json.startsWith("{") ? EOFException.class.getName() : null);
    } finally {
      release.countDown();
      client.close();
    }
  }

  private static InfluxDB client(Source source) {
    return client(source, new Dispatcher());
  }

  private static InfluxDB client(Source source, Dispatcher dispatcher) {
    BufferedSource bufferedSource = Okio.buffer(source);
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .addInterceptor(
                chain -> {
                  Response.Builder response = response(chain);
                  if (chain.request().url().encodedPath().endsWith("/ping")) {
                    return response
                        .header("X-Influxdb-Version", "1.8.10")
                        .body(ResponseBody.create(MediaType.parse("text/plain"), ""))
                        .build();
                  }
                  return response
                      .body(
                          ResponseBody.create(
                              MediaType.parse("application/json"), -1, bufferedSource))
                      .build();
                });
    return InfluxDBFactory.connect("http://localhost:8086", builder);
  }

  private static Response.Builder response(Interceptor.Chain chain) {
    return new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK");
  }

  private static void assertTelemetry(String errorType) {
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
    if (errorType == null) {
      assertThat(testing.spans().get(0).getEvents()).isEmpty();
    } else {
      assertThat(testing.spans().get(0).getEvents())
          .extracting(event -> event.getAttributes().get(EXCEPTION_TYPE))
          .containsExactly(errorType);
    }
  }
}
