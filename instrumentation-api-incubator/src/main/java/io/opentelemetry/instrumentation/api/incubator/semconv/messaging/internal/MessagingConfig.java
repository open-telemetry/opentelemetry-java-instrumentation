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
    boolean configured = hasHeadersConfig(messagingConfig, systemPropertyFallback);
    IncludeExclude selector = getHeaders(messagingConfig, systemPropertyFallback);
    if (!selector.isEmpty() || configured) {
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

  // visible for testing
  static IncludeExclude getHeaders(
      DeclarativeConfigProperties messagingConfig, boolean systemPropertyFallback) {
    DeclarativeConfigProperties headers = messagingConfig.get("headers/development");
    List<String> included =
        getList(
            headers,
            "included",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.included",
            systemPropertyFallback);
    List<String> excluded =
        getList(
            headers,
            "excluded",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.excluded",
            systemPropertyFallback);
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
      warnDeprecatedFlatProperty(
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
    DeclarativeConfigProperties deprecatedConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "messaging");
    DeclarativeConfigProperties headers = deprecatedConfig.get("headers/development");
    String deprecatedHeadersPrefix =
        DEPRECATED_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.";
    List<String> included =
        getList(headers, "included", deprecatedHeadersPrefix + "included", systemPropertyFallback);
    List<String> excluded =
        getList(headers, "excluded", deprecatedHeadersPrefix + "excluded", systemPropertyFallback);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    if (included != null || excluded != null) {
      if (included != null) {
        warnDeprecatedFlatProperty(
            deprecatedHeadersPrefix + "included",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.included");
      }
      if (excluded != null) {
        warnDeprecatedFlatProperty(
            deprecatedHeadersPrefix + "excluded",
            COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.excluded");
      }
      return selector;
    }
    return null;
  }

  private static boolean hasHeadersConfig(
      DeclarativeConfigProperties messagingConfig, boolean systemPropertyFallback) {
    DeclarativeConfigProperties headers = messagingConfig.get("headers/development");
    return getList(
                headers,
                "included",
                COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.included",
                systemPropertyFallback)
            != null
        || getList(
                headers,
                "excluded",
                COMMON_MESSAGING_PROPERTY_PREFIX + ".experimental.headers.excluded",
                systemPropertyFallback)
            != null;
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

  private static void warnDeprecatedFlatProperty(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " system property is deprecated and will be removed in 3.0. Use "
              + replacementProperty
              + " instead.");
    }
  }

  private MessagingConfig() {}
}
