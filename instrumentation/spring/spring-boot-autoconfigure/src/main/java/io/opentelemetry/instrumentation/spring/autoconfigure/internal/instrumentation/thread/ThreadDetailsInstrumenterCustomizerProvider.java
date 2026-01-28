/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.incubator.thread.ThreadDetailsAttributesExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {
  @Override
  public void customize(InstrumenterCustomizer customizer) {
    OpenTelemetry openTelemetry = customizer.getOpenTelemetry();
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      if (((ExtendedOpenTelemetry) openTelemetry)
          .getInstrumentationConfig("common")
          .get("thread_details")
          .getBoolean("enabled", false)) {
        customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
      }
    }
  }
}
