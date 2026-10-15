/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingConfig;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExperimentalConfig {

  private static final ExperimentalConfig instance =
      new ExperimentalConfig(GlobalOpenTelemetry.get());

  private final boolean controllerTelemetryEnabled;
  private final boolean viewTelemetryEnabled;
  private final IncludeExclude messagingHeaders;
  private final boolean messagingReceiveInstrumentationEnabled;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    return instance;
  }

  public ExperimentalConfig(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    this.controllerTelemetryEnabled =
        commonConfig.get("controller_telemetry").getBoolean("enabled", false);
    this.viewTelemetryEnabled = commonConfig.get("view_telemetry").getBoolean("enabled", false);
    this.messagingHeaders = MessagingConfig.getHeaders(openTelemetry);
    this.messagingReceiveInstrumentationEnabled =
        MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, false);
  }

  public boolean controllerTelemetryEnabled() {
    return controllerTelemetryEnabled;
  }

  public boolean viewTelemetryEnabled() {
    return viewTelemetryEnabled;
  }

  public boolean messagingReceiveInstrumentationEnabled() {
    return messagingReceiveInstrumentationEnabled;
  }

  /**
   * Returns the messaging header selector, or an {@linkplain IncludeExclude#isEmpty() empty}
   * selector when no headers should be captured.
   */
  public IncludeExclude getMessagingHeaders() {
    return messagingHeaders;
  }
}
