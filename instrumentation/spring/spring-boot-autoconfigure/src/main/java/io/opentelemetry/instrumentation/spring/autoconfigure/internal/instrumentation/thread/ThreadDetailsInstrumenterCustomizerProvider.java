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

  // Static because this class is loaded via ServiceLoader (SPI), not managed by Spring DI.
  // Set by OpenTelemetryAutoConfiguration#openTelemetry before instrumenters are built,
  // ensuring the flag is available when customize() is called during
  // Instrumenter.builder().build().
  private static volatile boolean enabled;

  /** Called from OpenTelemetryAutoConfiguration for declarative config. */
  public static void configureDeclarativeConfig(Environment environment) {
    enabled =
        Boolean.TRUE.equals(
            environment.getProperty(
                "otel.distribution.spring_starter.thread_details_enabled", Boolean.class));
  }

  /** Called from OpenTelemetryAutoConfiguration for properties-based config. */
  public static void configureProperties(Environment environment) {
    enabled =
        Boolean.TRUE.equals(
            environment.getProperty(
                "otel.instrumentation.common.thread-details.enabled", Boolean.class));
  }

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (enabled) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }
}
