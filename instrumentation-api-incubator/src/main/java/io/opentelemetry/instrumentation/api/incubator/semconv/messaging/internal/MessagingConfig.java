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
import javax.annotation.Nullable;

/**
 * Resolves common and instrumentation-specific messaging configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingConfig {

  private static final IncludeExclude NONE = IncludeExclude.builder().build();
  private static final String COMMON_MESSAGING_PROPERTY_PREFIX =
      "otel.instrumentation.common.messaging";

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
    DeclarativeConfigProperties messagingConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("messaging");
    DeclarativeConfigProperties headers = messagingConfig.get("headers");
    List<String> included = headers.getScalarList("included", String.class);
    List<String> excluded = headers.getScalarList("excluded", String.class);
    if (systemPropertyFallback) {
      if (included == null) {
        included = SystemProperty.getList(COMMON_MESSAGING_PROPERTY_PREFIX + ".headers.included");
      }
      if (excluded == null) {
        excluded = SystemProperty.getList(COMMON_MESSAGING_PROPERTY_PREFIX + ".headers.excluded");
      }
    }

    if (included == null && excluded == null) {
      return NONE;
    }
    return IncludeExclude.builder()
        .setIncluded(included == null ? emptyList() : included)
        .setExcluded(excluded == null ? emptyList() : excluded)
        .build();
  }

  /**
   * Returns whether messaging receive telemetry is enabled.
   *
   * @param systemPropertyFallback whether to fall back to flat system properties when declarative
   *     configuration does not contain a value. This is needed by library instrumentation entry
   *     points that have no programmatic configuration surface.
   */
  public static boolean isReceiveTelemetryEnabled(
      OpenTelemetry openTelemetry, boolean systemPropertyFallback) {
    DeclarativeConfigProperties messagingConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common").get("messaging");
    Boolean enabled =
        getBoolean(
            messagingConfig.get("receive_telemetry/development"),
            "enabled",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.receive-telemetry.enabled",
            systemPropertyFallback);
    return enabled != null ? enabled : false;
  }

  /**
   * Returns whether an instrumentation should emit a creation span for each message in a batch
   * send.
   *
   * <p>Resolves the instrumentation-specific {@code message_create_spans} declarative setting and
   * {@code message-create-spans.enabled} flat property, then falls back to the common messaging
   * setting.
   */
  public static boolean isBatchSendMessageCreationSpansEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return isBatchSendMessageCreationSpansEnabled(openTelemetry, instrumentationName, false);
  }

  /**
   * Returns whether an instrumentation should emit a creation span for each message in a batch
   * send.
   *
   * <p>Resolves the instrumentation-specific {@code message_create_spans} declarative setting and
   * {@code message-create-spans.enabled} flat property, then falls back to the common messaging
   * setting.
   *
   * @param systemPropertyFallback whether to fall back to flat system properties when declarative
   *     configuration does not contain a value. This is needed by library instrumentation entry
   *     points that have no programmatic configuration surface.
   */
  public static boolean isBatchSendMessageCreationSpansEnabled(
      OpenTelemetry openTelemetry, String instrumentationName, boolean systemPropertyFallback) {
    String instrumentationPropertyPrefix =
        "otel.instrumentation." + instrumentationName.replace('_', '-');
    Boolean enabled =
        getBoolean(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName)
                .get("message_create_spans"),
            "enabled",
            instrumentationPropertyPrefix + ".message-create-spans.enabled",
            systemPropertyFallback);
    if (enabled != null) {
      return enabled;
    }

    enabled =
        getBoolean(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
                .get("messaging")
                .get("message_create_spans"),
            "enabled",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".message-create-spans.enabled",
            systemPropertyFallback);
    return enabled != null ? enabled : true;
  }

  @Nullable
  private static Boolean getBoolean(
      DeclarativeConfigProperties config,
      String name,
      String flatProperty,
      boolean systemPropertyFallback) {
    Boolean value = config.getBoolean(name);
    if (value != null) {
      return value;
    }
    return systemPropertyFallback ? SystemProperty.getBoolean(flatProperty) : null;
  }

  private MessagingConfig() {}
}
