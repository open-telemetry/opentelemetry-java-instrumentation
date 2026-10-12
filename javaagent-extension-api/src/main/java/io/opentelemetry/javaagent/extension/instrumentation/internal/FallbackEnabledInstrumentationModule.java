/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;

/**
 * Instrumentation module enabled unless explicitly disabled, even when common default enablement is
 * disabled.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class FallbackEnabledInstrumentationModule extends InstrumentationModule {

  protected FallbackEnabledInstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    super(mainInstrumentationName, additionalInstrumentationNames);
  }

  @Override
  public boolean defaultEnabled() {
    return true;
  }
}
