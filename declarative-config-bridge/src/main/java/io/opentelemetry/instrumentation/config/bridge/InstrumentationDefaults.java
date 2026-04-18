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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Defines instrumentation defaults that work with both traditional property-based configuration and
 * declarative configuration.
 *
 * <p>Navigation mirrors {@link io.opentelemetry.api.incubator.config.DeclarativeConfigProperties}:
 * read-side uses {@code config.getStructured(name).getString(key)}; write-side uses {@code
 * defaults.get(name).setDefault(key, value)}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * InstrumentationDefaults defaults = new InstrumentationDefaults();
 * defaults.get("micrometer").setDefault("base_time_unit", "s");
 * defaults.get("log4j_appender").setDefault("experimental_log_attributes", "true");
 *
 * // Declarative config mode: inject into model
 * customizer.addModelCustomizer(model -> defaults.applyToModel(model));
 *
 * // Traditional mode: translate to ConfigProperties
 * autoConfiguration.addPropertiesSupplier(defaults::toConfigProperties);
 * }</pre>
 */
public final class InstrumentationDefaults {

  private final Map<String, InstrumentationProperties> instrumentations = new LinkedHashMap<>();

  /**
   * Returns the defaults builder for the given instrumentation, creating it if absent. Mirrors
   * {@code DeclarativeConfigProperties.getStructured(name)} on the read side.
   */
  public InstrumentationProperties get(String instrumentation) {
    return instrumentations.computeIfAbsent(instrumentation, k -> new InstrumentationProperties());
  }

  /** Translates defaults to {@code otel.instrumentation.*} keys for auto-configuration. */
  public Map<String, String> toConfigProperties() {
    HashMap<String, String> map = new HashMap<>();
    instrumentations.forEach(
        (instrumentation, properties) ->
            properties.properties.forEach(
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
  @CanIgnoreReturnValue
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

    for (Map.Entry<String, InstrumentationProperties> entry : instrumentations.entrySet()) {
      String name = entry.getKey();
      Map<String, String> defaults = entry.getValue().properties;

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

  /** Defaults for a single instrumentation. Keys use underscore notation. */
  public static final class InstrumentationProperties {

    private final Map<String, String> properties = new LinkedHashMap<>();

    private InstrumentationProperties() {}

    /**
     * Sets a default value for a property. Keys use underscore notation (e.g. {@code
     * base_time_unit}); they are translated to hyphen notation when producing {@code
     * otel.instrumentation.*} keys.
     *
     * @return {@code this} for chaining
     */
    @CanIgnoreReturnValue
    public InstrumentationProperties setDefault(String key, String value) {
      properties.put(key, value);
      return this;
    }
  }
}
