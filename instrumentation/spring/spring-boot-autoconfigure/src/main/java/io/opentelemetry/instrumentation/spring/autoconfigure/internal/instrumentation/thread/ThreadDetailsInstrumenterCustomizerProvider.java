/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  private static final String LEGACY_PROPERTY =
      "otel.instrumentation.common.thread-details.enabled";
  private static final String DECLARATIVE_PROPERTY =
      "otel.distribution.spring_starter.thread_details_enabled";

  // Static because this class is loaded via ServiceLoader (SPI), not managed by Spring DI.
  // Set by OpenTelemetryAutoConfiguration#openTelemetry before instrumenters are built,
  // ensuring the flag is available when customize() is called during
  // Instrumenter.builder().build().
  private static volatile boolean enabled;

  /** Called from OpenTelemetryAutoConfiguration for declarative config. */
  public static void configureDeclarativeConfig(Environment environment) {
    // Declarative config uses the distro-scoped key, but we still accept the legacy property so
    // both Spring configuration paths resolve the same feature flag.
    enabled = readEnabled(environment, DECLARATIVE_PROPERTY, LEGACY_PROPERTY);
  }

  /** Called from OpenTelemetryAutoConfiguration for properties-based config. */
  public static void configureProperties(Environment environment) {
    // Properties-based config keeps the legacy toggle, but we fall back to the declarative key so
    // a single setting still works if it is shared across config styles.
    enabled = readEnabled(environment, LEGACY_PROPERTY, DECLARATIVE_PROPERTY);
  }

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (enabled) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }

  private static boolean readEnabled(
      Environment environment, String primaryProperty, String fallbackProperty) {
    Boolean enabled = environment.getProperty(primaryProperty, Boolean.class);
    if (enabled != null) {
      return enabled;
    }
    enabled = environment.getProperty(fallbackProperty, Boolean.class);
    return Boolean.TRUE.equals(enabled);
  }
}
