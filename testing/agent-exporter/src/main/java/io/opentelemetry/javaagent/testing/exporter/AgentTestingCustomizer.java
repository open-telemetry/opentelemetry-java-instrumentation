/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import static io.opentelemetry.sdk.internal.ScopeConfiguratorBuilder.nameEquals;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.logs.internal.LoggerConfig;
import io.opentelemetry.sdk.logs.internal.SdkLoggerProviderUtil;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.internal.MeterConfig;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.internal.SdkTracerProviderUtil;
import io.opentelemetry.sdk.trace.internal.TracerConfig;
import java.time.Duration;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AgentTestingCustomizer implements AutoConfigurationCustomizerProvider {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.create(AgentTestingExporterFactory.spanExporter));

  static final MetricReader metricReader =
      PeriodicMetricReader.builder(AgentTestingExporterFactory.metricExporter)
          // Set really long interval. We'll call forceFlush when we need the metrics
          // instead of collecting them periodically.
          .setInterval(Duration.ofNanos(Long.MAX_VALUE))
          .build();

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfigurationCustomizer) {
    autoConfigurationCustomizer.addTracerProviderCustomizer(
        (tracerProvider, config) -> {
          SdkTracerProviderUtil.addTracerConfiguratorCondition(
              tracerProvider, nameEquals("disabled-tracer"), TracerConfig.disabled());

          // span processor is responsible for exporting spans, not adding it disables exporting of
          // the spans
          if (config.getBoolean("testing.exporter.enabled", true)) {
            return tracerProvider.addSpanProcessor(spanProcessor);
          }
          return tracerProvider;
        });

    autoConfigurationCustomizer.addMeterProviderCustomizer(
        (meterProvider, config) -> {
          SdkMeterProviderUtil.addMeterConfiguratorCondition(
              meterProvider, nameEquals("disabled-meter"), MeterConfig.disabled());

          // metric reader is responsible for exporting metrics, not adding it disables exporting of
          // the metrics
          if (config.getBoolean("testing.exporter.enabled", true)) {
            return meterProvider.registerMetricReader(metricReader);
          }
          return meterProvider;
        });

    autoConfigurationCustomizer.addLoggerProviderCustomizer(
        (logProvider, config) -> {
          SdkLoggerProviderUtil.addLoggerConfiguratorCondition(
              logProvider, nameEquals("disabled-logger"), LoggerConfig.disabled());

          // log record processor is responsible for exporting logs, not adding it disables
          // exporting of the logs
          if (config.getBoolean("testing.exporter.enabled", true)) {
            return logProvider.addLogRecordProcessor(
                SimpleLogRecordProcessor.create(AgentTestingExporterFactory.logExporter));
          }
          return logProvider;
        });
  }
}
