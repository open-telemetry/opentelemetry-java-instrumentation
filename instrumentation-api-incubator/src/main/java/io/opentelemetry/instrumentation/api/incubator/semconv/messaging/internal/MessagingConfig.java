/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.EXPERIMENTAL;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Resolves common and instrumentation-specific messaging configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MessagingConfig {

  private static final Logger logger = Logger.getLogger(MessagingConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();
  private static final IncludeExclude NONE = IncludeExclude.builder().build();
  private static final String COMMON_MESSAGING_PROPERTY_PREFIX =
      "otel.instrumentation.common.messaging";
  private static final String DEPRECATED_MESSAGING_PROPERTY_PREFIX =
      "otel.instrumentation.messaging";

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
    IncludeExclude selector =
        getHeaders(
            messagingConfig.get("headers/development"),
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.",
            systemPropertyFallback);
    if (selector != null) {
      return selector;
    }

    if (!SemconvStability.v3Preview(openTelemetry)) {
      // TODO: remove the deprecated flat messaging names in 3.0.
      selector = getDeprecatedHeaderAliases(openTelemetry, systemPropertyFallback);
      if (selector != null) {
        return selector;
      }
    }

    selector =
        SelectorConfig.resolveDeprecatedCapture(
            messagingConfig,
            "messaging",
            "headers",
            "common.messaging",
            EXPERIMENTAL,
            systemPropertyFallback);
    return selector == null ? NONE : selector;
  }

  /**
   * Returns the header selector configured under the given node and flat property prefix, or {@code
   * null} when nothing is configured to be captured. An empty selector is equivalent to no selector
   * at all, matching flat configuration where empty property values cannot be distinguished from
   * unset ones.
   */
  @Nullable
  private static IncludeExclude getHeaders(
      DeclarativeConfigProperties headers,
      String flatPropertyPrefix,
      boolean systemPropertyFallback) {
    List<String> included =
        getList(headers, "included", flatPropertyPrefix + "included", systemPropertyFallback);
    List<String> excluded =
        getList(headers, "excluded", flatPropertyPrefix + "excluded", systemPropertyFallback);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    return selector.isEmpty() ? null : selector;
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
    if (enabled != null || SemconvStability.v3Preview(openTelemetry)) {
      return enabled != null && enabled;
    }

    // TODO: remove the deprecated flat messaging name in 3.0.
    String deprecatedProperty =
        DEPRECATED_MESSAGING_PROPERTY_PREFIX + ".experimental.receive-telemetry.enabled";
    enabled =
        getBoolean(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "messaging")
                .get("receive_telemetry/development"),
            "enabled",
            deprecatedProperty,
            systemPropertyFallback);
    if (enabled != null) {
      warnDeprecatedProperty(
          deprecatedProperty,
          COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.receive-telemetry.enabled");
      return enabled;
    }
    return false;
  }

  public static boolean isBatchSendMessageCreationSpansEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return isBatchSendMessageCreationSpansEnabled(openTelemetry, instrumentationName, false);
  }

  /**
   * Returns whether an instrumentation should emit a creation span for each message in a batch
   * send.
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
  private static IncludeExclude getDeprecatedHeaderAliases(
      OpenTelemetry openTelemetry, boolean systemPropertyFallback) {
    String deprecatedHeadersPrefix =
        DEPRECATED_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.";
    IncludeExclude selector =
        getHeaders(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "messaging")
                .get("headers/development"),
            deprecatedHeadersPrefix,
            systemPropertyFallback);
    if (selector == null) {
      return null;
    }
    if (!selector.getIncluded().isEmpty()) {
      warnDeprecatedProperty(
          deprecatedHeadersPrefix + "included",
          COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.included");
    }
    if (!selector.getExcluded().isEmpty()) {
      warnDeprecatedProperty(
          deprecatedHeadersPrefix + "excluded",
          COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.excluded");
    }
    return selector;
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

  private static void warnDeprecatedProperty(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " setting and the equivalent declarative configuration property are deprecated"
              + " and will be removed in 3.0. Use "
              + replacementProperty
              + " or equivalent declarative configuration instead.");
    }
  }

  private MessagingConfig() {}
}
