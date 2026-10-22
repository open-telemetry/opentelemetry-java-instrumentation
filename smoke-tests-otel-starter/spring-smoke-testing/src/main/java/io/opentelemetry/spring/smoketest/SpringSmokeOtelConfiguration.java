/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.logs.ConfigurableLogRecordExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.ConfigurableMetricExporterProvider;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SpringSmokeOtelConfiguration {

  private static final String MEMORY_EXPORTER = "memory";

  @Bean
  AutoConfigurationCustomizerProvider autoConfigurationCustomizerProvider() {
    return provider -> provider.addPropertiesSupplier(SpringSmokeOtelConfiguration::getProperties);
  }

  private static Map<String, String> getProperties() {
    Map<String, String> properties = new HashMap<>();
    // We set the export interval of the metrics to 100 ms. The default value is 1 minute.
    properties.put("otel.metric.export.interval", "100");
    // We set the export interval of the spans to 100 ms. The default value is 5 seconds.
    properties.put("otel.bsp.schedule.delay", "100");
    // We set the export interval of the logs to 100 ms. The default value is 1 second.
    properties.put("otel.blrp.schedule.delay", "100");
    properties.put("otel.traces.exporter", MEMORY_EXPORTER);
    properties.put("otel.metrics.exporter", MEMORY_EXPORTER);
    properties.put("otel.logs.exporter", MEMORY_EXPORTER);
    return Collections.unmodifiableMap(properties);
  }

  @Bean
  ConfigurableMetricExporterProvider otlpMetricExporterProvider() {
    return new ConfigurableMetricExporterProvider() {
      @Override
      public MetricExporter createExporter(ConfigProperties configProperties) {
        return SpringSmokeTestRunner.testMetricExporter;
      }

      @Override
      public String getName() {
        return MEMORY_EXPORTER;
      }
    };
  }

  @Bean
  ConfigurableSpanExporterProvider otlpSpanExporterProvider() {
    return new ConfigurableSpanExporterProvider() {
      @Override
      public SpanExporter createExporter(ConfigProperties configProperties) {
        return SpringSmokeTestRunner.testSpanExporter;
      }

      @Override
      public String getName() {
        return MEMORY_EXPORTER;
      }
    };
  }

  @Bean
  ConfigurableLogRecordExporterProvider otlpLogRecordExporterProvider() {
    return new ConfigurableLogRecordExporterProvider() {
      @Override
      public LogRecordExporter createExporter(ConfigProperties configProperties) {
        return SpringSmokeTestRunner.testLogRecordExporter;
      }

      @Override
      public String getName() {
        return MEMORY_EXPORTER;
      }
    };
  }
}
