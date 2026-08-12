/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Resolves the common messaging header selector, shared by every messaging instrumentation so that
 * precedence and deprecation warnings are uniform.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingConfig {

  private static final Logger logger = Logger.getLogger(MessagingConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

  private static final String DEPRECATED_CAPTURE_HEADERS =
      "otel.instrumentation.messaging.experimental.capture-headers";
  private static final String HEADERS_INCLUDED =
      "otel.instrumentation.messaging.experimental.headers.included";
  private static final String HEADERS_EXCLUDED =
      "otel.instrumentation.messaging.experimental.headers.excluded";

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
    DeclarativeConfigProperties headers = messagingConfig.get("headers/development");
    List<String> included = getList(headers, "included", HEADERS_INCLUDED, systemPropertyFallback);
    List<String> excluded = getList(headers, "excluded", HEADERS_EXCLUDED, systemPropertyFallback);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    // an empty selector is equivalent to no selector at all, matching flat configuration where
    // empty property values cannot be distinguished from unset ones
    if (!selector.isEmpty()) {
      return selector;
    }

    List<String> deprecatedIncluded =
        getList(
            messagingConfig,
            "capture_headers/development",
            DEPRECATED_CAPTURE_HEADERS,
            systemPropertyFallback);
    if (deprecatedIncluded == null) {
      return NONE;
    }

    if (warnedDeprecatedProperties.add(DEPRECATED_CAPTURE_HEADERS)) {
      logger.warning(
          "The "
              + DEPRECATED_CAPTURE_HEADERS
              + " setting and the equivalent declarative configuration property"
              + " are deprecated and may be removed in the next minor release. Use "
              + HEADERS_INCLUDED
              + " or equivalent declarative configuration instead.");
    }
    return deprecatedIncluded.isEmpty()
        ? NONE
        : IncludeExclude.builder().setIncluded(deprecatedIncluded).build();
  }

  @Nullable
  private static List<String> getList(
      DeclarativeConfigProperties config,
      String name,
      String flatProperty,
      boolean systemPropertyFallback) {
    List<String> value = config.getScalarList(name, String.class);
    if (value != null) {
      return value;
    }
    return systemPropertyFallback ? SystemProperty.getList(flatProperty) : null;
  }

  private MessagingConfig() {}
}
