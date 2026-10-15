/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.internal;

import static java.util.Collections.singletonMap;

import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.internal.OpenTelemetryConfigurationModelAccessor;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SdkInternalTelemetryCustomizer
    implements AutoConfigurationCustomizerProvider, DeclarativeConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer customizer) {
    customizer.addPropertiesSupplier(
        () -> singletonMap("otel.experimental.sdk.telemetry.version", "latest"));
  }

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          ExperimentalInstrumentationModel instrumentation =
              OpenTelemetryConfigurationModelAccessor.getInstrumentation(model);
          if (instrumentation == null) {
            instrumentation = new ExperimentalInstrumentationModel();
          }
          ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
          if (java == null) {
            java = new ExperimentalLanguageSpecificInstrumentationModel();
            instrumentation.setJava(java);
          }
          ExperimentalLanguageSpecificInstrumentationPropertyModel sdk =
              java.getAdditionalProperties()
                  .computeIfAbsent(
                      "otel_sdk",
                      key -> new ExperimentalLanguageSpecificInstrumentationPropertyModel());
          sdk.getAdditionalProperties().putIfAbsent("internal_telemetry_version", "latest");
          // The accessor copies the subtree, so populate it before attaching it to the model.
          OpenTelemetryConfigurationModelAccessor.setInstrumentation(model, instrumentation);
          return model;
        });
  }

  @Override
  public int order() {
    // make sure it runs BEFORE all the user-provided customizers
    return Integer.MIN_VALUE;
  }
}
