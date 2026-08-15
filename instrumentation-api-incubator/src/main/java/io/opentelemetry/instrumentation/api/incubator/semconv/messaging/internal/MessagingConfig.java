/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.api.internal.CapturedNames;
import io.opentelemetry.instrumentation.api.internal.CapturedNames.CaseSensitivity;

/**
 * Resolves the common messaging header selector, shared by every messaging instrumentation so that
 * precedence and deprecation warnings are uniform.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingConfig {

  /**
   * Returns the messaging headers to capture, which is {@linkplain CapturedNames#isEmpty() empty}
   * when no headers are configured to be captured.
   */
  public static CapturedNames getHeaders(OpenTelemetry openTelemetry) {
    return getHeaders(openTelemetry, false);
  }

  /**
   * Returns the messaging headers to capture, which is {@linkplain CapturedNames#isEmpty() empty}
   * when no headers are configured to be captured.
   *
   * @param systemPropertyFallback whether to fall back to the flat system properties when the
   *     declarative configuration does not contain a value. This is needed by library
   *     instrumentation entry points that have no programmatic configuration surface.
   */
  public static CapturedNames getHeaders(
      OpenTelemetry openTelemetry, boolean systemPropertyFallback) {
    return getHeaders(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("messaging"),
        systemPropertyFallback);
  }

  // visible for testing
  static CapturedNames getHeaders(
      DeclarativeConfigProperties messagingConfig, boolean systemPropertyFallback) {
    return SelectorConfig.resolveCapturedNames(
        messagingConfig,
        "messaging",
        "headers",
        systemPropertyFallback,
        CaseSensitivity.CASE_SENSITIVE);
  }

  private MessagingConfig() {}
}
