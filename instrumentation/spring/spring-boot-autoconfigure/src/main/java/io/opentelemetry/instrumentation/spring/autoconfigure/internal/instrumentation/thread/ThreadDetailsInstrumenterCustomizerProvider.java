/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import static java.util.logging.Level.WARNING;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.DistributionPropertyModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  private static final Logger logger =
      Logger.getLogger(ThreadDetailsInstrumenterCustomizerProvider.class.getName());

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
    enabled = parseConfig(model.getDistribution()).isThreadDetailsEnabled();
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

  private static SpringStarterDistributionConfig parseConfig(@Nullable DistributionModel distribution) {
    if (distribution != null) {
      DistributionPropertyModel springStarter =
          distribution.getAdditionalProperties().get("spring_starter");
      if (springStarter != null) {
        try {
          return mapper.convertValue(springStarter, SpringStarterDistributionConfig.class);
        } catch (IllegalArgumentException e) {
          logger.log(WARNING, "Failed to parse distribution.spring_starter configuration", e);
        }
      }
    }
    return new SpringStarterDistributionConfig(null);
  }

  private static final class SpringStarterDistributionConfig {
    private final boolean threadDetailsEnabled;

    @JsonCreator
    private SpringStarterDistributionConfig(
        @Nullable @JsonProperty("thread_details_enabled") Boolean threadDetailsEnabled) {
      this.threadDetailsEnabled = threadDetailsEnabled != null ? threadDetailsEnabled : false;
    }

    private boolean isThreadDetailsEnabled() {
      return threadDetailsEnabled;
    }
  }
}
