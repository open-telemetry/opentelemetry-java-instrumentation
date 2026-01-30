/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;

@AutoService(InstrumenterCustomizerProvider.class)
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (customizer.getOpenTelemetry() instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry openTelemetry = (ExtendedOpenTelemetry) customizer.getOpenTelemetry();
      ConfigProvider configProvider = openTelemetry.getConfigProvider();
      boolean threadDetailsEnabled =
          configProvider instanceof ConfigPropertiesBackedConfigProvider
              ?
              // declarative configuration not in use, read from ConfigProperties
              ((ConfigPropertiesBackedConfigProvider) configProvider)
                  .getConfigProperties()
                  .getBoolean("otel.javaagent.add-thread-details", true)
              : openTelemetry
                  .getInstrumentationConfig("common")
                  .get("thread_details")
                  .getBoolean("enabled", false);

      if (threadDetailsEnabled) {
        customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
      }
    }
  }
}
