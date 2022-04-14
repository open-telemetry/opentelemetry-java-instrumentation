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

  public boolean controllerTelemetryEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    boolean suppressControllerSpans =
        config.getBoolean(
            "otel.instrumentation.common.experimental.suppress-controller-spans", false);
    return config.getBoolean(
        "otel.instrumentation.common.experimental.controller-telemetry.enabled",
        !suppressControllerSpans);
  }

  public boolean viewTelemetryEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    boolean suppressViewSpans =
        config.getBoolean("otel.instrumentation.common.experimental.suppress-view-spans", false);
    return config.getBoolean(
        "otel.instrumentation.common.experimental.view-telemetry.enabled", !suppressViewSpans);
  }

  public boolean messagingReceiveInstrumentationEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    boolean receiveSpansSuppressed =
        config.getBoolean(
            "otel.instrumentation.common.experimental.suppress-messaging-receive-spans", true);
    return config.getBoolean(
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled",
        !receiveSpansSuppressed);
  }
}
