/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;

/**
 * Resolves the common messaging header selector, shared by every messaging instrumentation so that
 * precedence and deprecation warnings are uniform.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingConfig {

  private static final IncludeExclude NONE = IncludeExclude.builder().build();

  /**
   * Returns the configured messaging header selector, or an {@linkplain IncludeExclude#isEmpty()
   * empty} selector when no headers are configured to be captured.
   */
  public static IncludeExclude getHeaders(OpenTelemetry openTelemetry) {
    return getHeaders(openTelemetry, false);
  }

  /**
   * Returns the configured messaging header selector, or an {@linkplain IncludeExclude#isEmpty()
   * empty} selector when no headers are configured to be captured.
   *
   * @param systemPropertyFallback whether to fall back to the flat system properties when the
   *     declarative configuration does not contain a value. This is needed by library
   *     instrumentation entry points that have no programmatic configuration surface.
   */
  public static IncludeExclude getHeaders(
      OpenTelemetry openTelemetry, boolean systemPropertyFallback) {
    return getHeaders(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("messaging"),
        systemPropertyFallback);
  }

  // visible for testing
  static IncludeExclude getHeaders(
      DeclarativeConfigProperties messagingConfig, boolean systemPropertyFallback) {
    IncludeExclude selector =
        SelectorConfig.resolve(messagingConfig, "messaging", "headers", systemPropertyFallback);
    return selector == null ? NONE : selector;
  }

  private MessagingConfig() {}
}
