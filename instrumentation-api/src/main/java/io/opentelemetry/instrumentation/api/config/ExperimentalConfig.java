/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

public final class ExperimentalConfig {

  private static final ExperimentalConfig instance = new ExperimentalConfig(Config.get());

  private final Config config;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    return instance;
  }

  public ExperimentalConfig(Config config) {
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
