/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.HashMap;
import java.util.Map;

/** Utility that applies {@link DefaultInstrumentationConfig} defaults to the declarative model. */
public final class DefaultInstrumentationConfigApplier {

  private DefaultInstrumentationConfigApplier() {}

  /**
   * Applies defaults to the declarative configuration model under {@code
   * instrumentation/development.java}. Existing values in the model take precedence; defaults are
   * only set for properties not already present.
   */
  @CanIgnoreReturnValue
  public static OpenTelemetryConfigurationModel applyToModel(
      DefaultInstrumentationConfig defaults, OpenTelemetryConfigurationModel model) {
    if (defaults.getDefaults().isEmpty()) {
      return model;
    }

    ExperimentalInstrumentationModel instrumentation = model.getInstrumentationDevelopment();
    if (instrumentation == null) {
      instrumentation = new ExperimentalInstrumentationModel();
      model.withInstrumentationDevelopment(instrumentation);
    }
    ExperimentalLanguageSpecificInstrumentationModel java = instrumentation.getJava();
    if (java == null) {
      java = new ExperimentalLanguageSpecificInstrumentationModel();
      instrumentation.withJava(java);
    }

    Map<String, ExperimentalLanguageSpecificInstrumentationPropertyModel> props =
        java.getAdditionalProperties();

    for (Map.Entry<String, String> entry : defaults.getDefaults().entrySet()) {
      applyDefault(props, entry.getKey(), entry.getValue());
    }

    return model;
  }

  private static void applyDefault(
      Map<String, ExperimentalLanguageSpecificInstrumentationPropertyModel> props,
      String declarativePath,
      String value) {
    String[] segments = declarativePath.split("\\.");
    ExperimentalLanguageSpecificInstrumentationPropertyModel propertyModel =
        props.computeIfAbsent(
            segments[0], key -> new ExperimentalLanguageSpecificInstrumentationPropertyModel());
    Map<String, Object> target = propertyModel.getAdditionalProperties();
    for (int i = 1; i < segments.length - 1; i++) {
      Object child = target.get(segments[i]);
      if (child == null) {
        Map<String, Object> nested = new HashMap<>();
        target.put(segments[i], nested);
        target = nested;
        continue;
      }
      if (!(child instanceof Map)) {
        return;
      }
      // Nested defaults only create string-keyed maps, so this cast is safe here.
      @SuppressWarnings("unchecked")
      Map<String, Object> nested = (Map<String, Object>) child;
      target = nested;
    }
    target.putIfAbsent(segments[segments.length - 1], value);
  }
}
