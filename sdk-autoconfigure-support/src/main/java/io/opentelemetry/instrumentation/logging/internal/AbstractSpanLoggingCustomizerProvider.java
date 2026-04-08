/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logging.internal;

import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ConsoleExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SimpleSpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanExporterModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

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
