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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExperimentalConfig {

  private static final Logger logger = Logger.getLogger(ExperimentalConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();
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
    return telemetryEnabled("controller_telemetry");
  }

  public boolean viewTelemetryEnabled() {
    return telemetryEnabled("view_telemetry");
  }

  private boolean telemetryEnabled(String name) {
    Boolean enabled = commonConfig.get(name).getBoolean("enabled");
    if (enabled != null) {
      return enabled;
    }
    // Keep the deprecated /development spelling until 3.0.
    if (!commonConfig.getBoolean("v3_preview", false)) {
      enabled = commonConfig.get(name + "/development").getBoolean("enabled");
      if (enabled != null) {
        String oldProperty =
            "otel.instrumentation.common.experimental." + name.replace('_', '-') + ".enabled";
        if (warnedDeprecatedProperties.add(oldProperty)) {
          logger.warning(
              "The "
                  + oldProperty
                  + " setting and the equivalent declarative configuration property"
                  + " are deprecated and will be removed in 3.0. Use "
                  + "otel.instrumentation.common."
                  + name.replace('_', '-')
                  + ".enabled or equivalent declarative configuration instead.");
        }
        return enabled;
      }
    }
    return false;
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
