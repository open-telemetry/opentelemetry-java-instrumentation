/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationPropertyModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines instrumentation defaults that work with both traditional property-based configuration and
 * declarative configuration.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * InstrumentationDefaults defaults = new InstrumentationDefaults();
 * defaults.setDefault("micrometer", "base_time_unit", "s");
 * defaults.setDefault("log4j_appender", "experimental_log_attributes", "true");
 *
 * // Declarative config mode: inject into model
 * customizer.addModelCustomizer(model -> defaults.applyToModel(model));
 *
 * // Traditional mode: translate to ConfigProperties
 * autoConfiguration.addPropertiesSupplier(defaults::toConfigProperties);
 * }</pre>
 */
public final class InstrumentationDefaults {

  private final Map<String, Map<String, String>> instrumentations = new LinkedHashMap<>();

  /**
   * Sets a default value for an instrumentation property. Keys use underscore notation (e.g. {@code
   * base_time_unit}); they are translated to hyphen notation when producing property keys.
   *
   * @return {@code this} for chaining
   */
  public InstrumentationDefaults setDefault(String instrumentation, String key, String value) {
    instrumentations.computeIfAbsent(instrumentation, k -> new LinkedHashMap<>()).put(key, value);
    return this;
  }

  /** Translates defaults to {@code otel.instrumentation.*} keys for auto-configuration. */
  public Map<String, String> toConfigProperties() {
    HashMap<String, String> map = new HashMap<>();
    instrumentations.forEach(
        (instrumentation, properties) ->
            properties.forEach(
                (key, value) ->
                    map.put(
                        "otel.instrumentation."
                            + instrumentation.replace('_', '-')
                            + "."
                            + key.replace('_', '-'),
                        value)));
    return map;
  }

  /**
   * Applies defaults to the declarative configuration model under {@code
   * instrumentation/development.java}. Existing values in the model take precedence; defaults are
   * only set for properties not already present.
   */
  public OpenTelemetryConfigurationModel applyToModel(OpenTelemetryConfigurationModel model) {
    if (instrumentations.isEmpty()) {
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

    for (Map.Entry<String, Map<String, String>> entry : instrumentations.entrySet()) {
      String name = entry.getKey();
      Map<String, String> defaults = entry.getValue();

      ExperimentalLanguageSpecificInstrumentationPropertyModel propModel = props.get(name);
      if (propModel == null) {
        propModel = new ExperimentalLanguageSpecificInstrumentationPropertyModel();
        props.put(name, propModel);
      }

      for (Map.Entry<String, String> defaultEntry : defaults.entrySet()) {
        propModel
            .getAdditionalProperties()
            .putIfAbsent(defaultEntry.getKey(), defaultEntry.getValue());
      }
    }

    return model;
  }
}
