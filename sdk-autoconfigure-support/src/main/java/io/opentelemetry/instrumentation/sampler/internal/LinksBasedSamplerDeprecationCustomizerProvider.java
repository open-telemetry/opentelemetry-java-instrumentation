/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import io.opentelemetry.instrumentation.config.internal.DeclarativeConfigV3Preview;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Warns when the bundled links-based sampler is selected, or rejects it when the v3 preview is
 * enabled.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LinksBasedSamplerDeprecationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static final Logger logger =
      Logger.getLogger(LinksBasedSamplerDeprecationCustomizerProvider.class.getName());

  @Override
  public void customize(AutoConfigurationCustomizer customizer) {
    AtomicBoolean warned = new AtomicBoolean();
    customizer.addSamplerCustomizer(
        (sampler, config) -> {
          warnIfSelected(config, warned);
          return sampler;
        });
  }

  private static void warnIfSelected(ConfigProperties config, AtomicBoolean warned) {
    if (!"linksbased_parentbased_always_on".equals(config.getString("otel.traces.sampler"))) {
      return;
    }
    if (config.getBoolean(DeclarativeConfigV3Preview.V3_PREVIEW_PROPERTY, false)) {
      throw new ConfigurationException(
          "The linksbased_parentbased_always_on sampler is not supported when the v3 preview is"
              + " enabled");
    }
    if (warned.compareAndSet(false, true)) {
      logger.warning(
          "The otel.traces.sampler=linksbased_parentbased_always_on sampler is deprecated in the"
              + " Java agent and Spring Boot starter and will be removed in 3.0; there is no"
              + " replacement.");
    }
  }

  @Override
  public int order() {
    return Integer.MAX_VALUE;
  }
}
