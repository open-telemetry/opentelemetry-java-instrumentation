/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtelResourceProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.OtlpExporterProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.PropagationProperties;
import io.opentelemetry.instrumentation.spring.autoconfigure.properties.SpringConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication;
import io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestController;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      OtelSpringStarterSmokeTest.TestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // We set the export interval of the metrics to 100 ms. The default value is 1 minute.
      "otel.metric.export.interval=100",
      // We set the export interval of the spans to 100 ms. The default value is 5 seconds.
      "otel.bsp.schedule.delay=100",
      // We set the export interval of the logs to 100 ms. The default value is 1 second.
      "otel.blrp.schedule.delay=100",
      // The headers are simply set here to make sure that headers can be parsed
      "otel.exporter.otlp.headers.c=3",
      "otel.traces.exporter=memory",
      "otel.metrics.exporter=memory",
      "otel.logs.exporter=memory"
    })
class OtelSpringStarterSmokeTest {

  public static final InMemoryMetricExporter METRIC_EXPORTER =
      InMemoryMetricExporter.create(AggregationTemporality.DELTA);
  private static final InMemoryLogRecordExporter LOG_RECORD_EXPORTER =
      InMemoryLogRecordExporter.create();
  public static final InMemorySpanExporter SPAN_EXPORTER = InMemorySpanExporter.create();

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private Environment environment;
  @Autowired private PropagationProperties propagationProperties;
  @Autowired private OtelResourceProperties otelResourceProperties;
  @Autowired private OtlpExporterProperties otlpExporterProperties;

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

    @Bean
    @Order(1)
    AutoConfigurationCustomizerProvider hiddenPropagatorCustomizer() {
      return customizer ->
          customizer.addResourceCustomizer(
              (resource, config) ->
                  resource.merge(
                      Resource.create(
                          Attributes.of(
                              AttributeKey.booleanKey("keyFromResourceCustomizer"), false))));
    }

    @Bean
    @Order(2)
    AutoConfigurationCustomizerProvider propagatorCustomizer() {
      return customizer ->
          customizer.addResourceCustomizer(
              (resource, config) ->
                  resource.merge(
                      Resource.create(
                          Attributes.of(
                              AttributeKey.booleanKey("keyFromResourceCustomizer"), true))));
    }
  }

  @Test
  void propertyConversion() {
    ConfigProperties configProperties =
        SpringConfigProperties.create(
            environment,
            otlpExporterProperties,
            otelResourceProperties,
            propagationProperties,
            DefaultConfigProperties.createFromMap(
                Collections.singletonMap("otel.exporter.otlp.headers", "a=1,b=2")));
    assertThat(configProperties.getMap("otel.exporter.otlp.headers"))
        .containsEntry("a", "1")
        .containsEntry("b", "2")
        .containsEntry("c", "3");
    assertThat(configProperties.getList("otel.propagators")).containsExactly("b3");
  }

  @Test
  void shouldSendTelemetry() {

    testRestTemplate.getForObject(OtelSpringStarterSmokeTestController.URL, String.class);

    await()
        .atMost(Duration.ofSeconds(1))
        .until(() -> SPAN_EXPORTER.getFinishedSpanItems().size() == 2);

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
                                DbIncubatingAttributes.DB_STATEMENT,
                                "create table test_table (id bigint not null, primary key (id))")),
            traceAssert ->
                traceAssert.hasSpansSatisfyingExactly(
                    spanDataAssert ->
                        spanDataAssert
                            .hasKind(SpanKind.SERVER)
                            .hasResourceSatisfying(
                                r ->
                                    r.hasAttribute(
                                            AttributeKey.booleanKey("keyFromResourceCustomizer"),
                                            true)
                                        .hasAttribute(
                                            AttributeKey.stringKey("attributeFromYaml"), "true")
                                        .hasAttribute(
                                            OpenTelemetryAssertions.satisfies(
                                                ServiceIncubatingAttributes.SERVICE_INSTANCE_ID,
                                                AbstractCharSequenceAssert::isNotBlank)))
                            .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
                            .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200L)
                            .hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping")));

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
            CodeIncubatingAttributes.CODE_NAMESPACE, "org.springframework.boot.StartupInfoLogger");
  }
}
