/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
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
    properties = {"otel.exporter.otlp.enabled=false", "otel.metric.export.interval=100"
      // We set the export interval of the metrics to 100 ms. The default value is 1 minute.
    })
class OtelSpringStarterSmokeTest {

  public static final InMemoryMetricExporter METRIC_EXPORTER =
      InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  private static final InMemoryLogRecordExporter LOG_RECORD_EXPORTER =
      InMemoryLogRecordExporter.create();
  public static final InMemorySpanExporter SPAN_EXPORTER = InMemorySpanExporter.create();

  @Autowired private TestRestTemplate testRestTemplate;

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
  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  void shouldSendTelemetry() throws InterruptedException {

    testRestTemplate.getForObject(OtelSpringStarterSmokeTestController.URL, String.class);

    Thread.sleep(5_000); // Sleep time could be potentially reduced and perhaps removed with
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8962
    // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/8963

    List<SpanData> exportedSpans = SPAN_EXPORTER.getFinishedSpanItems();

    // Span
    TracesAssert.assertThat(exportedSpans)
        .hasSize(2)
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
                            .hasAttribute(SemanticAttributes.HTTP_METHOD, "GET")
                            .hasAttribute(SemanticAttributes.HTTP_STATUS_CODE, 200L)
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
        .isEqualTo("Initializing Spring DispatcherServlet 'dispatcherServlet'");
  }
}
