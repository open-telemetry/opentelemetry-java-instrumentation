/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      OtelSpringStarterSmokeTest.TestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "otel.traces.exporter=none",
      "otel.metrics.exporter=none",
      "otel.logs.exporter=none",
      "otel.metric.export.interval=100",
      "otel.exporter.otlp.headers=a=1,b=2",
      // We set the export interval of the metrics to 100 ms. The default value is 1 minute.
      // the headers are simply set here to make sure that headers can be parsed
    })
class OtelSpringStarterSmokeTest {

  public static final InMemoryMetricExporter METRIC_EXPORTER =
      InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  private static final InMemoryLogRecordExporter LOG_RECORD_EXPORTER =
      InMemoryLogRecordExporter.create();
  public static final InMemorySpanExporter SPAN_EXPORTER = InMemorySpanExporter.create();

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private ConfigProperties configProperties;

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {
    @Bean
    public MetricExporter metricExporter() {
      return METRIC_EXPORTER;
    }

    @Bean
    public SpanExporter spanExporter() {
      return SPAN_EXPORTER;
    }

    @Bean
    public LogRecordExporter logRecordExporter() {
      return LOG_RECORD_EXPORTER;
    }
  }

  @Test
  void propertyConversion() {
    assertThat(configProperties.getMap("otel.exporter.otlp.headers"))
        .containsEntry("a", "1")
        .containsEntry("b", "2");
    assertThat(configProperties.getList("otel.propagators")).containsExactly("b3");
  }

  @Test
  void shouldSendTelemetry() throws InterruptedException {

    testRestTemplate.getForObject(OtelSpringStarterSmokeTestController.URL, String.class);

    Thread.sleep(5_000); // Sleep time could be potentially reduced and perhaps removed with
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8962
    // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8963

    List<SpanData> exportedSpans = SPAN_EXPORTER.getFinishedSpanItems();

    // Span
    TracesAssert.assertThat(exportedSpans)
        .hasTracesSatisfyingExactly(
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    spanDataAssert ->
                        spanDataAssert
                            .hasKind(SpanKind.CLIENT)
                            .hasAttribute(
                                SemanticAttributes.DB_STATEMENT,
                                "create table test_table (id bigint not null, primary key (id))")),
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    spanDataAssert ->
                        spanDataAssert
                            .hasKind(SpanKind.SERVER)
                            .hasAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, "GET")
                            .hasAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
                            .hasAttribute(SemanticAttributes.HTTP_ROUTE, "/ping")));

    // Metric
    List<MetricData> exportedMetrics = METRIC_EXPORTER.getFinishedMetricItems();
    assertThat(exportedMetrics)
        .as("Should contain " + OtelSpringStarterSmokeTestController.TEST_HISTOGRAM + " metric.")
        .anySatisfy(
            metric -> {
              String metricName = metric.getName();
              assertThat(metricName).isEqualTo(OtelSpringStarterSmokeTestController.TEST_HISTOGRAM);
            });

    // Log
    List<LogRecordData> logs = LOG_RECORD_EXPORTER.getFinishedLogRecordItems();
    LogRecordData firstLog = logs.get(0);
    assertThat(firstLog.getBody().asString())
        .as("Should instrument logs")
        .startsWith("Starting ")
        .contains(this.getClass().getSimpleName());
    assertThat(firstLog.getAttributes().asMap())
        .as("Should capture code attributes")
        .containsEntry(
            SemanticAttributes.CODE_NAMESPACE, "org.springframework.boot.StartupInfoLogger");
  }
}
