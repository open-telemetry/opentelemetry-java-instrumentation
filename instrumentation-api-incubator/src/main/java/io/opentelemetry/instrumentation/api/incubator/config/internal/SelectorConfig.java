/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
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
 * <p>The property names are derived from the instrumentation and selector names. Existing overloads
 * resolve experimental included/excluded selectors. Overloads accepting {@link Stability} can
 * instead resolve stable selectors. Deprecated capture settings remain experimental.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class SelectorConfig {

  private static final Logger logger = Logger.getLogger(SelectorConfig.class.getName());
  private static final Set<String> warnings = ConcurrentHashMap.newKeySet();

  /**
   * The stability of the included/excluded selector configuration. This does not change the
   * stability of the deprecated capture setting.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public enum Stability {
    STABLE,
    EXPERIMENTAL
  }

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Note that {@code null} is returned rather than an {@linkplain IncludeExclude#isEmpty()
   * empty} selector because an empty selector matches every value.
   *
   * <p>Values of the deprecated include-only setting that contain {@code *} or {@code ?} are
   * ignored and logged, since that setting matches values literally and never supported wildcards.
   */
  @Nullable
  public static IncludeExclude resolve(
      DeclarativeConfigProperties config, String instrumentationName, String selectorName) {
    return resolve(config, instrumentationName, selectorName, Stability.EXPERIMENTAL, false);
  }

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Values of the deprecated include-only setting that contain {@code *} or {@code ?} are
   * ignored and logged, since that setting matches values literally and never supported wildcards.
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
    return resolve(
        config, instrumentationName, selectorName, Stability.EXPERIMENTAL, systemPropertyFallback);
  }

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Values of the deprecated include-only setting that contain {@code *} or {@code ?} are
   * ignored and logged, since that setting matches values literally and never supported wildcards.
   *
   * @param stability whether the included/excluded selector is stable or experimental
   */
  @Nullable
  public static IncludeExclude resolve(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      Stability stability) {
    return resolve(config, instrumentationName, selectorName, stability, false);
  }

  /**
   * Returns the configured selector, or {@code null} when nothing is configured to be captured.
   *
   * <p>Values of the deprecated include-only setting that contain {@code *} or {@code ?} are
   * ignored and logged, since that setting matches values literally and never supported wildcards.
   *
   * @param stability whether the included/excluded selector is stable or experimental
   * @param systemPropertyFallback whether to fall back to the flat system properties when the
   *     declarative configuration does not contain a value. This is needed by library
   *     instrumentation entry points that have no programmatic configuration surface.
   */
  @Nullable
  public static IncludeExclude resolve(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      Stability stability,
      boolean systemPropertyFallback) {
    IncludeExclude selector =
        getSelector(config, instrumentationName, selectorName, stability, systemPropertyFallback);
    if (selector != null) {
      return selector;
    }
    List<String> deprecated =
        getDeprecated(config, instrumentationName, selectorName, stability, systemPropertyFallback);
    return DeprecatedCaptureNames.toSelector(
        deprecated,
        "the "
            + deprecatedFlatProperty(instrumentationName, selectorName)
            + " setting or equivalent declarative configuration",
        flatProperty(instrumentationName, selectorName, ".included", stability)
            + " or equivalent declarative configuration");
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
    return resolveLegacyLiteral(config, instrumentationName, selectorName, Stability.EXPERIMENTAL);
  }

  /**
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolve}, the values of the deprecated include-only setting are matched
   * literally, except for the single value {@code "*"} which matches everything. This preserves the
   * behavior of settings that documented {@code "*"} as capturing all values, where interpreting
   * the remaining values as globs would silently widen what is captured.
   *
   * @param stability whether the included/excluded selector is stable or experimental
   */
  @Nullable
  public static Predicate<String> resolveLegacyLiteral(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      Stability stability) {
    IncludeExclude selector =
        getSelector(config, instrumentationName, selectorName, stability, false);
    if (selector != null) {
      return selector::matches;
    }
    List<String> deprecated =
        getDeprecated(config, instrumentationName, selectorName, stability, false);
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
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolve}, the deprecated setting is a boolean, where {@code true} selects
   * every value and {@code false} selects none.
   */
  @Nullable
  public static Predicate<String> resolveLegacyBoolean(
      DeclarativeConfigProperties config, String instrumentationName, String selectorName) {
    return resolveLegacyBoolean(
        config, instrumentationName, selectorName, selectorName, Stability.EXPERIMENTAL);
  }

  /**
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolve}, the deprecated setting is a boolean, where {@code true} selects
   * every value and {@code false} selects none.
   *
   * @param stability whether the included/excluded selector is stable or experimental
   */
  @Nullable
  public static Predicate<String> resolveLegacyBoolean(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      Stability stability) {
    return resolveLegacyBoolean(config, instrumentationName, selectorName, selectorName, stability);
  }

  /**
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolveLegacyBoolean(DeclarativeConfigProperties, String, String)}, the
   * deprecated boolean setting is named after {@code deprecatedSelectorName} instead of {@code
   * selectorName}, for settings that were not renamed consistently with their replacement.
   */
  @Nullable
  public static Predicate<String> resolveLegacyBoolean(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      String deprecatedSelectorName) {
    return resolveLegacyBoolean(
        config, instrumentationName, selectorName, deprecatedSelectorName, Stability.EXPERIMENTAL);
  }

  /**
   * Returns a predicate matching the configured selector, or {@code null} when nothing is
   * configured to be captured.
   *
   * <p>Unlike {@link #resolveLegacyBoolean(DeclarativeConfigProperties, String, String,
   * Stability)}, the deprecated boolean setting is named after {@code deprecatedSelectorName}
   * instead of {@code selectorName}, for settings that were not renamed consistently with their
   * replacement.
   *
   * @param stability whether the included/excluded selector is stable or experimental
   */
  @Nullable
  public static Predicate<String> resolveLegacyBoolean(
      DeclarativeConfigProperties config,
      String instrumentationName,
      String selectorName,
      String deprecatedSelectorName,
      Stability stability) {
    IncludeExclude selector =
        getSelector(config, instrumentationName, selectorName, stability, false);
    if (selector != null) {
      return selector::matches;
    }
    Boolean deprecated =
        config.getBoolean("capture_" + nodeName(deprecatedSelectorName) + "/development");
    if (deprecated == null) {
      return null;
    }
    warnDeprecated(instrumentationName, selectorName, deprecatedSelectorName, stability);
    return deprecated ? value -> true : null;
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
      Stability stability,
      boolean systemPropertyFallback) {
    requireNonNull(stability, "stability");
    DeclarativeConfigProperties node = config.get(selectorNodeName(selectorName, stability));
    List<String> included =
        getList(
            node,
            "included",
            flatProperty(instrumentationName, selectorName, ".included", stability),
            systemPropertyFallback);
    List<String> excluded =
        getList(
            node,
            "excluded",
            flatProperty(instrumentationName, selectorName, ".excluded", stability),
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
      Stability stability,
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
    warnDeprecated(instrumentationName, selectorName, selectorName, stability);
    return deprecated;
  }

  private static void warnDeprecated(
      String instrumentationName,
      String selectorName,
      String deprecatedSelectorName,
      Stability stability) {
    String flatProperty = deprecatedFlatProperty(instrumentationName, deprecatedSelectorName);
    warnOnce(
        flatProperty + ":" + stability + ":deprecated",
        "The "
            + flatProperty
            + " setting and the equivalent declarative configuration property are deprecated and"
            + " may be removed in the next minor release. Use "
            + flatProperty(instrumentationName, selectorName, ".included", stability)
            + " or equivalent declarative configuration instead.");
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

  private static String selectorNodeName(String selectorName, Stability stability) {
    String nodeName = nodeName(selectorName);
    return stability == Stability.EXPERIMENTAL ? nodeName + "/development" : nodeName;
  }

  private static String flatProperty(
      String instrumentationName, String selectorName, String suffix, Stability stability) {
    return "otel.instrumentation."
        + instrumentationName
        + (stability == Stability.EXPERIMENTAL ? ".experimental." : ".")
        + selectorName
        + suffix;
  }

  private static String deprecatedFlatProperty(String instrumentationName, String selectorName) {
    return "otel.instrumentation." + instrumentationName + ".experimental.capture-" + selectorName;
  }

  private SelectorConfig() {}
}
