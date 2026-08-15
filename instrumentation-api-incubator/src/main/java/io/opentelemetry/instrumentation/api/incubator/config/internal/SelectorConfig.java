/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.CapturedNames;
import io.opentelemetry.instrumentation.api.internal.CapturedNames.CaseSensitivity;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Resolves an {@code included}/{@code excluded} selector and its deprecated include-only
 * predecessor, so that precedence and deprecation warnings are uniform across instrumentations.
 *
 * <p>The property names are derived from the instrumentation and selector names. For {@code
 * ("messaging", "headers")} the declarative configuration is read from the {@code
 * headers/development} node of the supplied configuration, falling back to the deprecated {@code
 * capture_headers/development} node, and the corresponding flat properties are {@code
 * otel.instrumentation.messaging.experimental.headers.included}, {@code ...headers.excluded} and
 * {@code otel.instrumentation.messaging.experimental.capture-headers}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SelectorConfig {

  private static final Logger logger = Logger.getLogger(SelectorConfig.class.getName());
  private static final Set<String> warnings = ConcurrentHashMap.newKeySet();

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Note that {@code null} is returned rather than an {@linkplain IncludeExclude#isEmpty()
   * empty} selector because an empty selector matches every value.
   *
   * <p>Use {@link #resolveCapturedNames} instead when the selector has a deprecated predecessor
   * that took a list of literal names. This method folds those values into the returned selector,
   * where they are matched as globs, which silently widens what a deprecated value of {@code "*"}
   * captures.
   */
  @Nullable
  public static IncludeExclude resolve(
      DeclarativeConfigProperties config, String instrumentationName, String selectorName) {
    return resolve(config, instrumentationName, selectorName, false);
  }

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Use {@link #resolveCapturedNames} instead when the selector has a deprecated predecessor
   * that took a list of literal names. This method folds those values into the returned selector,
   * where they are matched as globs, which silently widens what a deprecated value of {@code "*"}
   * captures.
   *
   * @param systemPropertyFallback whether to fall back to the flat system properties when the
   *     declarative configuration does not contain a value. This is needed by library
   *     instrumentation entry points that have no programmatic configuration surface.
   */
  @Nullable
  public static IncludeExclude resolve(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      boolean systemPropertyFallback) {
    IncludeExclude selector =
        getSelector(config, instrumentationName, selectorName, systemPropertyFallback);
    if (selector != null) {
      return selector;
    }
    List<String> deprecated =
        getDeprecated(config, instrumentationName, selectorName, systemPropertyFallback);
    return deprecated == null || deprecated.isEmpty()
        ? null
        : IncludeExclude.builder().setIncluded(deprecated).build();
  }

  /**
   * Returns the configured selector resolved into the names to capture.
   *
   * <p>Unlike {@link #resolve}, the values of the deprecated include-only setting are matched
   * literally rather than as globs. Those settings never supported {@code *} as capturing
   * everything, so interpreting their values as globs would silently widen what is captured.
   *
   * @param systemPropertyFallback whether to fall back to the flat system properties when the
   *     declarative configuration does not contain a value. This is needed by library
   *     instrumentation entry points that have no programmatic configuration surface.
   */
  public static CapturedNames resolveCapturedNames(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      boolean systemPropertyFallback,
      CaseSensitivity caseSensitivity) {
    IncludeExclude selector =
        getSelector(config, instrumentationName, selectorName, systemPropertyFallback);
    if (selector != null) {
      return CapturedNames.create(selector, caseSensitivity);
    }
    return CapturedNames.createExact(
        getDeprecated(config, instrumentationName, selectorName, systemPropertyFallback),
        caseSensitivity);
  }

  /**
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolve}, the values of the deprecated include-only setting are matched
   * literally, except for the single value {@code "*"} which matches everything. This preserves the
   * behavior of settings that documented {@code "*"} as capturing all values, where interpreting
   * the remaining values as globs would silently widen what is captured.
   */
  @Nullable
  public static Predicate<String> resolveLegacyLiteral(
      DeclarativeConfigProperties config, String instrumentationName, String selectorName) {
    IncludeExclude selector = getSelector(config, instrumentationName, selectorName, false);
    if (selector != null) {
      return selector::matches;
    }
    List<String> deprecated = getDeprecated(config, instrumentationName, selectorName, false);
    return deprecated == null ? null : resolveLegacyLiteral(deprecated);
  }

  /**
   * Returns a predicate matching the given deprecated include-only values, or {@code null} when
   * nothing is configured to be captured.
   *
   * <p>The values are matched literally, except for the single value {@code "*"} which matches
   * everything. This preserves the behavior of settings that documented {@code "*"} as capturing
   * all values, where interpreting the remaining values as globs would silently widen what is
   * captured.
   */
  @Nullable
  public static Predicate<String> resolveLegacyLiteral(List<String> deprecatedValues) {
    if (deprecatedValues.isEmpty()) {
      return null;
    }
    if (deprecatedValues.size() == 1 && deprecatedValues.get(0).equals("*")) {
      return value -> true;
    }
    Set<String> exactValues = new HashSet<>(deprecatedValues);
    return exactValues::contains;
  }

  /**
   * Returns the configured selector, or {@code null} when it is not configured. An empty selector
   * is equivalent to no selector at all, matching flat configuration where empty property values
   * cannot be distinguished from unset ones.
   */
  @Nullable
  private static IncludeExclude getSelector(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      boolean systemPropertyFallback) {
    DeclarativeConfigProperties node = config.get(nodeName(selectorName) + "/development");
    List<String> included =
        getList(
            node,
            "included",
            flatProperty(instrumentationName, selectorName, ".included"),
            systemPropertyFallback);
    List<String> excluded =
        getList(
            node,
            "excluded",
            flatProperty(instrumentationName, selectorName, ".excluded"),
            systemPropertyFallback);
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(included == null ? emptyList() : included)
            .setExcluded(excluded == null ? emptyList() : excluded)
            .build();
    return selector.isEmpty() ? null : selector;
  }

  /**
   * Returns the values of the deprecated include-only setting, or {@code null} when it is not
   * configured, warning about its use.
   */
  @Nullable
  private static List<String> getDeprecated(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      boolean systemPropertyFallback) {
    String flatProperty = deprecatedFlatProperty(instrumentationName, selectorName);
    List<String> deprecated =
        getList(
            config,
            "capture_" + nodeName(selectorName) + "/development",
            flatProperty,
            systemPropertyFallback);
    if (deprecated == null) {
      return null;
    }
    warnOnce(
        flatProperty + ":deprecated",
        "The "
            + flatProperty
            + " setting and the equivalent declarative configuration property are deprecated and"
            + " may be removed in the next minor release. Use "
            + flatProperty(instrumentationName, selectorName, ".included")
            + " or equivalent declarative configuration instead.");
    return deprecated;
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

  private static void warnOnce(String key, String message) {
    if (warnings.add(key)) {
      logger.warning(message);
    }
  }

  private static String nodeName(String selectorName) {
    return selectorName.replace('-', '_');
  }

  private static String flatProperty(
      String instrumentationName, String selectorName, String suffix) {
    return "otel.instrumentation." + instrumentationName + ".experimental." + selectorName + suffix;
  }

  private static String deprecatedFlatProperty(String instrumentationName, String selectorName) {
    return "otel.instrumentation." + instrumentationName + ".experimental.capture-" + selectorName;
  }

  private SelectorConfig() {}
}
