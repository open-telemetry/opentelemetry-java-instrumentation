/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExperimentalConfig {

  private static final ExperimentalConfig instance =
      new ExperimentalConfig(AgentInstrumentationConfig.get());

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
    return config.getBoolean(
        "otel.instrumentation.common.experimental.controller-telemetry.enabled", false);
  }

  public boolean viewTelemetryEnabled() {
    return config.getBoolean(
        "otel.instrumentation.common.experimental.view-telemetry.enabled", false);
  }

  public boolean messagingReceiveInstrumentationEnabled() {
    return config.getBoolean(
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);
  }

  public boolean indyEnabled() {
    return config.getBoolean("otel.javaagent.experimental.indy", false);
  }

  public List<String> getMessagingHeaders() {
    return messagingHeaders;
  }
}
