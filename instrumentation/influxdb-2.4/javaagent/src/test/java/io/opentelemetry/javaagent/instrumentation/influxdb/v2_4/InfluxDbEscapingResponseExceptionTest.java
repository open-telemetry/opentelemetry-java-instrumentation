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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.squareup.moshi.JsonDataException;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.impl.InfluxDBImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;

class InfluxDbEscapingResponseExceptionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void malformedChunkEscapingResponseCallbackIsRecorded() throws InterruptedException {
    assumeTrue(!testLatestDeps());

    CountDownLatch callbackDispatchComplete = new CountDownLatch(1);
    AtomicBoolean resultCallbackInvoked = new AtomicBoolean();
    Executor callbackExecutor =
        command -> {
          try {
            command.run();
          } finally {
            callbackDispatchComplete.countDown();
          }
        };
    OkHttpClient.Builder clientBuilder =
        new OkHttpClient.Builder().addInterceptor(chain -> response(chain, "{\"results\":{}}"));

    try (InfluxDB client =
        new InfluxDBImpl(
            "http://localhost:8086",
            null,
            null,
            clientBuilder,
            new Retrofit.Builder().callbackExecutor(callbackExecutor),
            InfluxDB.ResponseFormat.JSON)) {
      client.query(
          new Query("SELECT * FROM cpu", "mydb"), 1, result -> resultCallbackInvoked.set(true));
      assertThat(callbackDispatchComplete.await(10, SECONDS)).isTrue();
      assertThat(resultCallbackInvoked.get()).isFalse();
    }

    String errorType = JsonDataException.class.getName();
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
                            equalTo(ERROR_TYPE, errorType))
                        .hasEventsSatisfyingExactly(event -> event.hasName("exception"))));
    assertThat(testing.spans().get(0).getEvents())
        .singleElement()
        .satisfies(
            event -> assertThat(event.getAttributes().get(EXCEPTION_TYPE)).isEqualTo(errorType));
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

  private static Response response(Interceptor.Chain chain, String body) {
    return new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(MediaType.parse("application/json"), body))
        .build();
  }
}
