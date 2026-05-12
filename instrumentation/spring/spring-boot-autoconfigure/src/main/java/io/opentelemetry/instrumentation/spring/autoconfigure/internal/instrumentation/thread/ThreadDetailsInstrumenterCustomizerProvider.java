/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.Map;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  private static final String LEGACY_PROPERTY =
      "otel.instrumentation.common.thread-details.enabled";

  private static final ObjectMapper mapper = new ObjectMapper();

  // Static because this class is loaded via ServiceLoader (SPI), not managed by Spring DI.
  // Set by OpenTelemetryAutoConfiguration#openTelemetry before instrumenters are built,
  // ensuring the flag is available when customize() is called during
  // Instrumenter.builder().build().
  private static volatile boolean enabled;

  /** Called from OpenTelemetryAutoConfiguration for declarative config. */
  public static void configureDeclarativeConfig(OpenTelemetryConfigurationModel model) {
    enabled = isEnabled(model);
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

  @SuppressWarnings("unchecked") // distribution and spring_starter nodes are nested maps
  private static boolean isEnabled(OpenTelemetryConfigurationModel model) {
    Map<String, Object> config = mapper.convertValue(model, new TypeReference<Map<String, Object>>() {});
    Object distribution = config.get("distribution");
    if (!(distribution instanceof Map)) {
      return false;
    }
    Object springStarter = ((Map<String, Object>) distribution).get("spring_starter");
    if (!(springStarter instanceof Map)) {
      return false;
    }
    Object enabled = ((Map<String, Object>) springStarter).get("thread_details_enabled");
    return Boolean.TRUE.equals(enabled);
  }
}
