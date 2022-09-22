/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;

import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExperimentalConfig {

  private static final ExperimentalConfig instance =
      new ExperimentalConfig(InstrumentationConfig.get());

  private final InstrumentationConfig config;
  private final List<String> messagingHeaders;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    return instance;
  }

  public ExperimentalConfig(InstrumentationConfig config) {
    this.config = config;
    messagingHeaders =
        config.getList("otel.instrumentation.messaging.experimental.capture-headers", emptyList());
  }

  public boolean controllerTelemetryEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    DeprecatedConfigPropertyWarning.warnIfUsed(
        config,
        "otel.instrumentation.common.experimental.suppress-controller-spans",
        "otel.instrumentation.common.experimental.controller-telemetry.enabled");
    boolean suppressControllerSpans =
        config.getBoolean(
            "otel.instrumentation.common.experimental.suppress-controller-spans", false);
    return config.getBoolean(
        "otel.instrumentation.common.experimental.controller-telemetry.enabled",
        !suppressControllerSpans);
  }

  public boolean viewTelemetryEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    DeprecatedConfigPropertyWarning.warnIfUsed(
        config,
        "otel.instrumentation.common.experimental.suppress-view-spans",
        "otel.instrumentation.common.experimental.view-telemetry.enabled");
    boolean suppressViewSpans =
        config.getBoolean("otel.instrumentation.common.experimental.suppress-view-spans", false);
    return config.getBoolean(
        "otel.instrumentation.common.experimental.view-telemetry.enabled", !suppressViewSpans);
  }

  public boolean messagingReceiveInstrumentationEnabled() {
    // TODO: remove that `suppress...` flag after 1.13 release
    DeprecatedConfigPropertyWarning.warnIfUsed(
        config,
        "otel.instrumentation.common.experimental.suppress-messaging-receive-spans",
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    boolean receiveSpansSuppressed =
        config.getBoolean(
            "otel.instrumentation.common.experimental.suppress-messaging-receive-spans", true);
    return config.getBoolean(
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled",
        !receiveSpansSuppressed);
  }

  public List<String> getMessagingHeaders() {
    return messagingHeaders;
  }
}
