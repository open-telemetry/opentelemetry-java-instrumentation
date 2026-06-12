/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

class SemconvSelectionResolver {
  // General declarative config, used for explicit general.<domain>.semconv.version and
  // general.<domain>.semconv.dual_emit settings.
  private final DeclarativeConfigProperties generalConfig;

  // Stable opt-in values, used when no explicit per-domain semconv config is present. Combines
  // general.stability_opt_in_list with OpenTelemetry-backed / ConfigPropertiesUtil-backed
  // otel.semconv-stability.opt-in values.
  private final Set<String> stableFlags;

  // Preview flags for service.peer, rpc, and messaging. Reads through OpenTelemetry-backed
  // java.common.semconv_stability.preview config, and falls back to ConfigPropertiesUtil for
  // otel.semconv-stability.preview in library instrumentation.
  private final Set<String> previewFlags;

  // Forces database and code to stable-only emission. For preview domains, v3 preview uses only
  // preview flags; non-preview combines stable and preview flags for backward compatibility.
  private final boolean v3Preview;

  SemconvSelectionResolver(
      OpenTelemetry openTelemetry, DeclarativeConfigProperties generalConfig, boolean v3Preview) {
    this(
        generalConfig,
        v3Preview,
        resolveStableOptInValues(openTelemetry, generalConfig),
        resolvePreviewValues(openTelemetry));
  }

  SemconvSelectionResolver(
      DeclarativeConfigProperties generalConfig,
      boolean v3Preview,
      Set<String> stableFlags,
      Set<String> previewFlags) {
    this.generalConfig = generalConfig;
    this.v3Preview = v3Preview;
    this.stableFlags = stableFlags;
    this.previewFlags = previewFlags;
  }

  SemconvSelection database() {
    if (v3Preview) {
      return stableOnly();
    }
    return resolveSemconvSelection("db", "database", stableFlags);
  }

  SemconvSelection code() {
    if (v3Preview) {
      return stableOnly();
    }
    return resolveSemconvSelection("code", "code", stableFlags);
  }

  SemconvSelection rpc() {
    return resolveSemconvSelection("rpc", "rpc", effectivePreviewFlags());
  }

  SemconvSelection messaging() {
    return resolveSemconvSelection("messaging", "messaging", effectivePreviewFlags());
  }

  // service.peer does not have an explicit general.<domain>.semconv config.
  SemconvSelection servicePeer() {
    return fromFlags("service.peer", effectivePreviewFlags());
  }

  static Set<String> resolveGeneralStableFlags(DeclarativeConfigProperties generalConfig) {
    String value = generalConfig.getString("stability_opt_in_list");
    if (value == null || value.trim().isEmpty()) {
      return emptySet();
    }
    return parseCommaSeparatedSet(value);
  }

  static Set<String> parseCommaSeparatedSet(String value) {
    return asList(value.split(",")).stream()
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .collect(toSet());
  }

  private SemconvSelection resolveSemconvSelection(
      String domainConfigName, String legacyKey, Set<String> fallbackValues) {
    SemconvSelection domainSelection = resolveDomainSemconvSelection(domainConfigName);
    if (domainSelection != null) {
      return domainSelection;
    }
    return fromFlags(legacyKey, fallbackValues);
  }

  private static SemconvSelection stableOnly() {
    return SemconvSelection.of(false, true);
  }

  private Set<String> effectivePreviewFlags() {
    if (v3Preview) {
      return previewFlags;
    }
    return combine(stableFlags, previewFlags);
  }

  @Nullable
  private SemconvSelection resolveDomainSemconvSelection(String domainConfigName) {
    DeclarativeConfigProperties semconvConfig = generalConfig.get(domainConfigName).get("semconv");
    Integer version = semconvConfig.getInt("version");
    if (version == null) {
      return null;
    }
    if (version == 0) {
      return SemconvSelection.of(true, false);
    }
    if (version == 1) {
      return SemconvSelection.of(semconvConfig.getBoolean("dual_emit", false), true);
    }
    return null;
  }

  private static SemconvSelection fromFlags(String key, Set<String> values) {
    return SemconvSelection.of(shouldEmitOld(key, values), shouldEmitStable(key, values));
  }

  private static boolean shouldEmitOld(String key, Set<String> optInValues) {
    if (optInValues.contains(key + "/dup")) {
      return true;
    }
    return !optInValues.contains(key);
  }

  private static boolean shouldEmitStable(String key, Set<String> optInValues) {
    return optInValues.contains(key) || optInValues.contains(key + "/dup");
  }

  private static Set<String> resolveOptInValues(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig =
        SemconvStability.getGeneralInstrumentationConfig(openTelemetry);
    return resolveStringList(
        generalConfig.get("semconv_stability"), "opt_in", "otel.semconv-stability.opt-in");
  }

  private static Set<String> resolveStableOptInValues(
      OpenTelemetry openTelemetry, DeclarativeConfigProperties generalConfig) {
    return combine(resolveGeneralStableFlags(generalConfig), resolveOptInValues(openTelemetry));
  }

  private static Set<String> resolvePreviewValues(OpenTelemetry openTelemetry) {
    // preview is Java-specific, so it lives under java.common rather than general
    DeclarativeConfigProperties commonConfig =
        SemconvStability.getInstrumentationConfig(openTelemetry, "common");
    return resolveStringList(
        commonConfig.get("semconv_stability"), "preview", "otel.semconv-stability.preview");
  }

  // ConfigPropertiesUtil is intentionally used as the fallback for library instrumentation.
  @SuppressWarnings("deprecation")
  private static Set<String> resolveStringList(
      DeclarativeConfigProperties config, String key, String fallbackProperty) {
    Set<String> values = new HashSet<>(config.getScalarList(key, String.class, emptyList()));
    if (!values.isEmpty()) {
      return values;
    }
    String value = ConfigPropertiesUtil.getString(fallbackProperty);
    return value == null ? emptySet() : parseCommaSeparatedSet(value);
  }

  private static Set<String> combine(Set<String> first, Set<String> second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty()) {
      return first;
    }
    Set<String> result = new HashSet<>(first);
    result.addAll(second);
    return result;
  }
}
