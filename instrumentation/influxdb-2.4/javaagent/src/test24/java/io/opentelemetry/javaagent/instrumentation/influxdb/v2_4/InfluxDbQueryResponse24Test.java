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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@SuppressWarnings("deprecation") // DB_QUERY_SUMMARY is emitted by the database instrumentation.
class InfluxDbQueryResponse24Test {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({
    "top,false", "statement,false", "success,false",
    "top,true", "statement,true", "success,true"
  })
  void queryResponse(String outcome, boolean withPrecision) {
    String body;
    if (outcome.equals("statement")) {
      body = "{\"results\":[{}, {\"error\":\"private query text\"}]}";
    } else if (outcome.equals("top")) {
      body = "{\"error\":\"private query text\"}";
    } else {
      body = "{\"results\":[{\"statement_id\":0}]}";
    }
    String errorType = outcome.equals("success") ? null : "_OTHER";
    OkHttpClient.Builder builder =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(MediaType.parse("application/json"), body))
                        .build());
    // 2.4 has no close method and no asynchronous query overloads.
    InfluxDB client = InfluxDBFactory.connect("http://localhost:8086", "user", "password", builder);
    Query query = new Query("SELECT * FROM cpu", "mydb");
    assertThat(withPrecision ? client.query(query, MILLISECONDS) : client.query(query)).isNotNull();

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
  }
}
