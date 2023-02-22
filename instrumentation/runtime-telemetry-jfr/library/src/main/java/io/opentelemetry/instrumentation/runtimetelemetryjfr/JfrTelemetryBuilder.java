/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Predicate;

/** Builder for {@link JfrTelemetry}. */
public class JfrTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  // Visible for testing
  final EnumMap<JfrFeature, Boolean> enabledFeatureMap;

  JfrTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    enabledFeatureMap = new EnumMap<>(JfrFeature.class);
    for (JfrFeature feature : JfrFeature.values()) {
      enabledFeatureMap.put(feature, feature.isDefaultEnabled());
    }
  }

  /** Enable telemetry collection for all {@link JfrFeature}s. */
  @CanIgnoreReturnValue
  public JfrTelemetryBuilder enableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::enableFeature);
    return this;
  }

  /** Enable telemetry collection associated with the {@link JfrFeature}. */
  @CanIgnoreReturnValue
  public JfrTelemetryBuilder enableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, true);
    return this;
  }

  /** Disable telemetry collection for all {@link JfrFeature}s. */
  @CanIgnoreReturnValue
  public JfrTelemetryBuilder disableAllFeatures() {
    Arrays.stream(JfrFeature.values()).forEach(this::disableFeature);
    return this;
  }

  /** Disable telemetry collection associated with the {@link JfrFeature}. */
  @CanIgnoreReturnValue
  public JfrTelemetryBuilder disableFeature(JfrFeature feature) {
    enabledFeatureMap.put(feature, false);
    return this;
  }

  /** Build and start an {@link JfrTelemetry} with the config from this builder. */
  public JfrTelemetry build() {
    Predicate<JfrFeature> featurePredicate =
        jfrFeature -> enabledFeatureMap.getOrDefault(jfrFeature, jfrFeature.isDefaultEnabled());
    return new JfrTelemetry(openTelemetry, featurePredicate);
  }
}
