/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ConsoleExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

/** Adds span logging exporter for debug mode */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class SpanLoggingCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          maybeEnableLoggingExporter(model);
          return model;
        });
  }

  private static void maybeEnableLoggingExporter(OpenTelemetryConfigurationModel model) {
    // read from system properties as it's an early init property and the config bridge is not
    // available here
    if (!"true".equals(System.getProperty("otel.javaagent.debug"))) {
      return;
    }
    // don't install another instance if the user has already explicitly requested it.
    if (loggingExporterIsAlreadyConfigured(model)) {
      return;
    }
    TracerProviderModel tracerProvider = model.getTracerProvider();
    if (tracerProvider == null) {
      tracerProvider = new TracerProviderModel();
      model.withTracerProvider(tracerProvider);
    }
    SpanProcessorModel processor =
        new SpanProcessorModel()
            .withSimple(
                new SimpleSpanProcessorModel()
                    .withExporter(new SpanExporterModel().withConsole(new ConsoleExporterModel())));
    tracerProvider.getProcessors().add(processor);
  }

  private static boolean loggingExporterIsAlreadyConfigured(OpenTelemetryConfigurationModel model) {
    TracerProviderModel tracerProvider = model.getTracerProvider();
    if (tracerProvider == null) {
      return false;
    }
    for (SpanProcessorModel processor : tracerProvider.getProcessors()) {
      SimpleSpanProcessorModel simple = processor.getSimple();
      if (simple == null) {
        continue;
      }
      SpanExporterModel exporter = simple.getExporter();
      if (exporter == null) {
        continue;
      }
      if (exporter.getConsole() != null) {
        return true;
      }
    }
    return false;
  }
}
