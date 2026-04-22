/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.api.incubator.config.ConfigProvider;
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

  // Static because this class is loaded via ServiceLoader (SPI), not managed by Spring DI.
  // Set by OpenTelemetryAutoConfiguration#openTelemetry before instrumenters are built,
  // ensuring the flag is available when customize() is called during
  // Instrumenter.builder().build().
  private static volatile boolean enabled;

  /** Called from OpenTelemetryAutoConfiguration for declarative config. */
  public static void configureDeclarativeConfig(ConfigProvider configProvider) {
    enabled =
        configProvider
            .getInstrumentationConfig()
            .get("java")
            .get("spring_starter")
            .get("thread_details")
            .getBoolean("enabled", false);
  }

  /** Called from OpenTelemetryAutoConfiguration for properties-based config. */
  public static void configureProperties(Environment environment) {
    enabled = Boolean.TRUE.equals(environment.getProperty(LEGACY_PROPERTY, Boolean.class));
  }

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (enabled) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }
}
