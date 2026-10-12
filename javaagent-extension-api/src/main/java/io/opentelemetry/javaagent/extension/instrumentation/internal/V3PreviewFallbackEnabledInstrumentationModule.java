/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;

/**
 * Instrumentation module whose enabled fallback is true when the v3 preview config flag is enabled.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@Deprecated // to be removed in 3.0
public abstract class V3PreviewFallbackEnabledInstrumentationModule extends InstrumentationModule {

  protected V3PreviewFallbackEnabledInstrumentationModule(
      String mainInstrumentationName, String... additionalInstrumentationNames) {
    super(mainInstrumentationName, additionalInstrumentationNames);
  }

  @Override
  public boolean defaultEnabled() {
    return super.defaultEnabled() || AgentCommonConfig.get().isV3Preview();
  }
}
