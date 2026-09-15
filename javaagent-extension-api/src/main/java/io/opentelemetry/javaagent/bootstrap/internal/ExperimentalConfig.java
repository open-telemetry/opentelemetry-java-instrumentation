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

  private final DeclarativeConfigProperties commonConfig;
  private final IncludeExclude messagingHeaders;
  private final boolean messagingReceiveInstrumentationEnabled;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    return instance;
  }

  public ExperimentalConfig(OpenTelemetry openTelemetry) {
    this.commonConfig = DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    this.messagingHeaders = MessagingConfig.getHeaders(openTelemetry);
    this.messagingReceiveInstrumentationEnabled =
        MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, false);
  }

  public boolean controllerTelemetryEnabled() {
    return commonConfig.get("controller_telemetry/development").getBoolean("enabled", false);
  }

  public boolean viewTelemetryEnabled() {
    return commonConfig.get("view_telemetry/development").getBoolean("enabled", false);
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
