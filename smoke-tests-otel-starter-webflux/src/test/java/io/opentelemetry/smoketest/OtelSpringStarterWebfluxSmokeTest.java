/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.opentelemetry.spring.smoketest.OtelSpringStarterWebfluxSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelSpringStarterWebfluxSmokeTestController;
import java.time.Duration;
import java.util.List;
import org.awaitility.core.ConditionEvaluationLogger;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.TimeoutEvent;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootTest(
    classes = {
      OtelSpringStarterWebfluxSmokeTestApplication.class,
      OtelSpringStarterWebfluxSmokeTest.TestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // We set the export interval of the metrics to 100 ms. The default value is 1 minute.
      "otel.metric.export.interval=100",
      // We set the export interval of the spans to 100 ms. The default value is 5 seconds.
      "otel.bsp.schedule.delay=100",
      // We set the export interval of the logs to 100 ms. The default value is 1 second.
      "otel.blrp.schedule.delay=100",
      "otel.traces.exporter=memory",
      "otel.metrics.exporter=memory",
      "otel.logs.exporter=memory"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OtelSpringStarterWebfluxSmokeTest {

  private static final InMemoryMetricExporter METRIC_EXPORTER =
      InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  private static final InMemoryLogRecordExporter LOG_RECORD_EXPORTER =
      InMemoryLogRecordExporter.create();
  private static final InMemorySpanExporter SPAN_EXPORTER = InMemorySpanExporter.create();
  private static final Logger logger =
      LoggerFactory.getLogger(OtelSpringStarterWebfluxSmokeTest.class);

  @Autowired private TestRestTemplate testRestTemplate;

  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {

    @Bean
    ConfigurableMetricExporterProvider otlpMetricExporterProvider() {
      return new ConfigurableMetricExporterProvider() {
        @Override
        public MetricExporter createExporter(ConfigProperties configProperties) {
          return METRIC_EXPORTER;
        }

        @Override
        public String getName() {
          return "memory";
        }
      };
    }

    @Bean
    ConfigurableSpanExporterProvider otlpSpanExporterProvider() {
      return new ConfigurableSpanExporterProvider() {
        @Override
        public SpanExporter createExporter(ConfigProperties configProperties) {
          return SPAN_EXPORTER;
        }

        @Override
        public String getName() {
          return "memory";
        }
      };
    }

    @Bean
    ConfigurableLogRecordExporterProvider otlpLogRecordExporterProvider() {
      return new ConfigurableLogRecordExporterProvider() {
        @Override
        public LogRecordExporter createExporter(ConfigProperties configProperties) {
          return LOG_RECORD_EXPORTER;
        }

        @Override
        public String getName() {
          return "memory";
        }
      };
    }
  }

  private static void resetExporters() {
    SPAN_EXPORTER.reset();
    METRIC_EXPORTER.reset();
    LOG_RECORD_EXPORTER.reset();
  }

  @Test
  @org.junit.jupiter.api.Order(1)
  void shouldSendTelemetry() {
    resetExporters();

    testRestTemplate.getForObject(OtelSpringStarterWebfluxSmokeTestController.PING, String.class);

    // Span
    TracesAssert.assertThat(expectSpans(2))
        .hasTracesSatisfyingExactly(
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    clientSpan ->
                        clientSpan
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfying(
                                a -> assertThat(a.get(UrlAttributes.URL_FULL)).endsWith("/ping")),
                    serverSpan ->
                        serverSpan
                            .hasKind(SpanKind.SERVER)
                            .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                            .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
                            .hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping")));
  }

  private static List<SpanData> expectSpans(int spans) {
    with()
        .conditionEvaluationListener(
            new ConditionEvaluationLogger() {
              @Override
              public void conditionEvaluated(EvaluatedCondition<Object> condition) {}

              @Override
              public void onTimeout(TimeoutEvent timeoutEvent) {
                logger.info("Spans: {}", SPAN_EXPORTER.getFinishedSpanItems());
              }
            })
        .await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> SPAN_EXPORTER.getFinishedSpanItems().size() == spans);

    return SPAN_EXPORTER.getFinishedSpanItems();
  }
}
