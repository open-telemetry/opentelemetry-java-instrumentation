/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanProcessorModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.SpanProcessorPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.TracerProviderModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the {@link ThreadDetailsSpanProcessor} (via the {@link ThreadDetailsComponentProvider}) to
 * the declarative configuration model when {@link #isEnabled(OpenTelemetryConfigurationModel)}
 * returns {@code true}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class AbstractThreadDetailsCustomizerProvider
    implements DeclarativeConfigurationCustomizerProvider {

  protected abstract boolean isEnabled(OpenTelemetryConfigurationModel model);

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          maybeAddThreadDetailsProcessor(model);
          return model;
        });
  }

  private void maybeAddThreadDetailsProcessor(OpenTelemetryConfigurationModel model) {
    if (!isEnabled(model)) {
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
    processors.add(
        new SpanProcessorModel()
            .withAdditionalProperty(
                ThreadDetailsComponentProvider.NAME, new SpanProcessorPropertyModel()));
  }
}
