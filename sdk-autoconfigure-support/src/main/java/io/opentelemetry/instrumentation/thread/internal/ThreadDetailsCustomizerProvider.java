/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.SpanProcessorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {
  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          TracerProviderModel tracerProvider = model.getTracerProvider();
          if (tracerProvider != null && isEnabled(model)) {
            tracerProvider
                .getProcessors()
                .add(new SpanProcessorModel().withAdditionalProperty("thread_details", null));
          }

          return model;
        });
  }

  private static boolean isEnabled(OpenTelemetryConfigurationModel model) {
    DeclarativeConfigProperties properties =
        SdkConfigProvider.create(model).getInstrumentationConfig();
    if (properties == null) {
      return false;
    }
    DeclarativeConfigProperties java =
        properties.getStructured("java", DeclarativeConfigProperties.empty());

    return java.getStructured("thread_details", DeclarativeConfigProperties.empty())
        .getBoolean("enabled", false);
  }
}
