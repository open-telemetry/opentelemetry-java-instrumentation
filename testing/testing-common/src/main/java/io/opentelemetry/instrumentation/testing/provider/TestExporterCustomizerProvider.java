/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.provider;

import static java.util.Collections.singletonList;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.ConsoleExporterModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.LogRecordExporterModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.LogRecordProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.LoggerProviderModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.MeterProviderModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.MetricReaderModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.PeriodicMetricReaderModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.PushMetricExporterModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SimpleLogRecordProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanExporterModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.TracerProviderModel;
import java.util.ArrayList;
import java.util.List;

public class TestExporterCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {

  @Override
  public int order() {
    return Integer.MIN_VALUE; // run before other customizers that might add exporters
  }

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    if (TestSpanExporterComponentProvider.getSpanExporter() == null) {
      // library test runner is not used, so we should not add the test exporters
      return;
    }
    customizer.addModelCustomizer(
        model -> {
          if (model.getTracerProvider() == null) {
            addTracerProvider(model);
          }
          if (model.getLoggerProvider() == null) {
            addLoggerProvider(model);
          }
          if (model.getMeterProvider() == null) {
            addMeterProvider(model);
          }
          return model;
        });
  }

  private static void addTracerProvider(OpenTelemetryConfigurationModel model) {
    // adds the following to the configuration:
    //    tracer_provider:
    //      processors:
    //        - simple:
    //            exporter:
    //              test:
    //        - simple:
    //            exporter:
    //              console:
    List<SpanProcessorModel> processors = new ArrayList<>();
    processors.add(getProcessorModel(new SpanExporterModel().setExtensionProperty("test", null)));
    processors.add(
        getProcessorModel(new SpanExporterModel().setConsole(new ConsoleExporterModel())));
    model.setTracerProvider(new TracerProviderModel().setProcessors(processors));
  }

  private static SpanProcessorModel getProcessorModel(SpanExporterModel exporter) {
    return new SpanProcessorModel().setSimple(new SimpleSpanProcessorModel().setExporter(exporter));
  }

  private static void addLoggerProvider(OpenTelemetryConfigurationModel model) {
    // adds the following to the configuration:
    //    logger_provider:
    //      processors:
    //        - simple:
    //            exporter:
    //              test:
    model.setLoggerProvider(
        new LoggerProviderModel()
            .setProcessors(
                singletonList(
                    new LogRecordProcessorModel()
                        .setSimple(
                            new SimpleLogRecordProcessorModel()
                                .setExporter(
                                    new LogRecordExporterModel()
                                        .setExtensionProperty("test", null))))));
  }

  private static void addMeterProvider(OpenTelemetryConfigurationModel model) {
    // adds the following to the configuration:
    //    meter_provider:
    //      readers:
    //        - periodic:
    //            interval: 1000000
    //            exporter:
    //              test:
    model.setMeterProvider(
        new MeterProviderModel()
            .setReaders(
                singletonList(
                    new MetricReaderModel()
                        .setPeriodic(
                            new PeriodicMetricReaderModel()
                                // Set really long interval. We'll call forceFlush when we need the
                                // metrics instead of collecting them periodically.
                                .setInterval(1000000)
                                .setExporter(
                                    new PushMetricExporterModel()
                                        .setExtensionProperty("test", null))))));
  }
}
