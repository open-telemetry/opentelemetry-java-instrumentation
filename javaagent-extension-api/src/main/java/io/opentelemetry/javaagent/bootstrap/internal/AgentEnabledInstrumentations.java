/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.api.internal.Initializer;

/**
 * Access to enabled instrumentations configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentEnabledInstrumentations {

  static EnabledInstrumentations instance;

  public static EnabledInstrumentations get() {
    return instance;
  }

  @Initializer
  public static void set(EnabledInstrumentations enabledInstrumentations) {
    AgentEnabledInstrumentations.instance = enabledInstrumentations;
  }

  // Visible for testing
  public static void resetForTest() {
    instance = null;
  }

  private AgentEnabledInstrumentations() {}
}
