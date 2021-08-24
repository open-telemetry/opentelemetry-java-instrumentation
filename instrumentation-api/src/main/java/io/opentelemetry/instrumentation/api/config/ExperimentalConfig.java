/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import org.checkerframework.checker.nullness.qual.Nullable;

public final class ExperimentalConfig {

  // lazy initialized, so that javaagent can set it, and library instrumentation can fall back and
  // read system properties
  @Nullable private static ExperimentalConfig instance = null;

  private final Config config;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    if (instance == null) {
      // this should only happen in library instrumentation
      //
      // no need to synchronize because worst case is creating instance more than once
      instance = new ExperimentalConfig(Config.get());
    }
    return instance;
  }

  private ExperimentalConfig(Config config) {
    this.config = config;
  }

  public boolean suppressControllerSpans() {
    return config.getBoolean(
        "otel.instrumentation.common.experimental.suppress-controller-spans", false);
  }

  public boolean suppressViewSpans() {
    return config.getBoolean("otel.instrumentation.common.experimental.suppress-view-spans", false);
  }
}
