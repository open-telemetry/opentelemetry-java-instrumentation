/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExperimentalConfig {

  private static final ExperimentalConfig instance =
      new ExperimentalConfig(GlobalOpenTelemetry.get());

  private final ExtendedDeclarativeConfigProperties commonConfig;
  private final List<String> messagingHeaders;

  /** Returns the global agent configuration. */
  public static ExperimentalConfig get() {
    return instance;
  }

  public ExperimentalConfig(OpenTelemetry openTelemetry) {
    this.commonConfig = DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");
    this.messagingHeaders =
        commonConfig
            .get("messaging")
            .getScalarList("capture_headers/development", String.class, emptyList());
  }

  public boolean controllerTelemetryEnabled() {
    return commonConfig.get("controller_telemetry/development").getBoolean("enabled", false);
  }

  public boolean viewTelemetryEnabled() {
    return commonConfig.get("view_telemetry/development").getBoolean("enabled", false);
  }

  public boolean messagingReceiveInstrumentationEnabled() {
    return commonConfig
        .get("messaging")
        .get("receive_telemetry/development")
        .getBoolean("enabled", false);
  }

  public List<String> getMessagingHeaders() {
    return messagingHeaders;
  }
}
