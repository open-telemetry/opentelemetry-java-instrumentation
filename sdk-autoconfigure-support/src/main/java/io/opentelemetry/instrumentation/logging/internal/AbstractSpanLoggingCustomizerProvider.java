/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logging.internal;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.declarativeconfig.internal.model.ConsoleExporterModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SpanExporterModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.declarativeconfig.internal.model.TracerProviderModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds span logging exporter for debug mode
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class AbstractSpanLoggingCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  protected abstract boolean isEnabled(OpenTelemetryConfigurationModel model);

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          maybeEnableLoggingExporter(model);
          return model;
        });
  }

  private void maybeEnableLoggingExporter(OpenTelemetryConfigurationModel model) {
    if (!isEnabled(model)) {
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
    List<SpanProcessorModel> processors = tracerProvider.getProcessors();
    if (processors == null) {
      processors = new ArrayList<>();
      tracerProvider.withProcessors(processors);
    }
    SpanProcessorModel processor =
        new SpanProcessorModel()
            .withSimple(
                new SimpleSpanProcessorModel()
                    .withExporter(new SpanExporterModel().withConsole(new ConsoleExporterModel())));
    processors.add(processor);
  }

  private static boolean loggingExporterIsAlreadyConfigured(OpenTelemetryConfigurationModel model) {
    TracerProviderModel tracerProvider = model.getTracerProvider();
    if (tracerProvider == null) {
      return false;
    }
    List<SpanProcessorModel> processors = tracerProvider.getProcessors();
    if (processors == null) {
      return false;
    }
    for (SpanProcessorModel processor : processors) {
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
