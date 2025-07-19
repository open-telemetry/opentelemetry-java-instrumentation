/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.provider;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.testing.exporter.AgentTestingExporterFactory;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ConsoleExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.LogRecordExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.LogRecordProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.LoggerProviderModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.MeterProviderModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.MetricReaderModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.PeriodicMetricReaderModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.PushMetricExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SimpleLogRecordProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class AgentTestExporterCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    AgentTestingExporterFactory.init();

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
    //              agent_test:
    //        - simple:
    //            exporter:
    //              console:
    List<SpanProcessorModel> processors = new ArrayList<>();
    processors.add(
        getProcessorModel(new SpanExporterModel().withAdditionalProperty("agent_test", null)));
    processors.add(
        getProcessorModel(new SpanExporterModel().withConsole(new ConsoleExporterModel())));
    model.withTracerProvider(new TracerProviderModel().withProcessors(processors));
  }

  private static SpanProcessorModel getProcessorModel(SpanExporterModel exporter) {
    return new SpanProcessorModel()
        .withSimple(new SimpleSpanProcessorModel().withExporter(exporter));
  }

  private static void addLoggerProvider(OpenTelemetryConfigurationModel model) {
    // adds the following to the configuration:
    //    logger_provider:
    //      processors:
    //        - simple:
    //            exporter:
    //              agent_test:
    model.withLoggerProvider(
        new LoggerProviderModel()
            .withProcessors(
                Collections.singletonList(
                    new LogRecordProcessorModel()
                        .withSimple(
                            new SimpleLogRecordProcessorModel()
                                .withExporter(
                                    new LogRecordExporterModel()
                                        .withAdditionalProperty("agent_test", null))))));
  }

  private static void addMeterProvider(OpenTelemetryConfigurationModel model) {
    // adds the following to the configuration:
    //    meter_provider:
    //      readers:
    //        - periodic:
    //            interval: 1000000
    //            exporter:
    //              agent_test:
    model.withMeterProvider(
        new MeterProviderModel()
            .withReaders(
                Collections.singletonList(
                    new MetricReaderModel()
                        .withPeriodic(
                            new PeriodicMetricReaderModel()
                                // Set really long interval. We'll call forceFlush when we need the
                                // metrics instead of collecting them periodically.
                                .withInterval(1000000)
                                .withExporter(
                                    new PushMetricExporterModel()
                                        .withAdditionalProperty("agent_test", null))))));
  }
}
