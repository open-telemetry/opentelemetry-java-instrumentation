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
  // Structured config, used for explicit general.<domain>.semconv.version,
  // general.<domain>.semconv.experimental, and general.<domain>.semconv.dual_emit settings.
  private final DeclarativeConfigProperties structuredConfig;

  // Stable opt-in values, used when no explicit per-domain semconv config is present. Reads
  // general.stability_opt_in_list first, and falls back to OpenTelemetry-backed /
  // SystemProperty-backed otel.semconv-stability.opt-in values.
  private final Set<String> stableFlags;

  // Preview flags for service.peer, rpc, and messaging. Reads through OpenTelemetry-backed
  // java.common.semconv_stability.preview config, and falls back to SystemProperty for
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
      DeclarativeConfigProperties structuredConfig,
      boolean v3Preview,
      Set<String> stableFlags,
      Set<String> previewFlags) {
    this.structuredConfig = structuredConfig;
    this.v3Preview = v3Preview;
    this.stableFlags = stableFlags;
    this.previewFlags = previewFlags;
  }

  SemconvMode database() {
    SemconvDomain.Builder domain = SemconvDomain.builder("db").flagKey("database");
    if (v3Preview) {
      domain.defaultMode(SemconvMode.V1_STABLE);
    } else {
      domain
          .defaultMode(SemconvMode.V0_STABLE)
          .otherSupportedModes(SemconvMode.V1_STABLE, SemconvMode.V1_STABLE.withDualEmit());
    }
    return resolveSemconvSelection(domain.build());
  }

  SemconvMode code() {
    SemconvDomain.Builder domain = SemconvDomain.builder("code");
    if (v3Preview) {
      domain.defaultMode(SemconvMode.V1_STABLE);
    } else {
      domain
          .defaultMode(SemconvMode.V0_STABLE)
          .otherSupportedModes(SemconvMode.V1_STABLE, SemconvMode.V1_STABLE.withDualEmit());
    }
    return resolveSemconvSelection(domain.build());
  }

  SemconvMode rpc() {
    return resolveSemconvSelection(
        SemconvDomain.builder("rpc")
            .defaultMode(SemconvMode.V0_STABLE)
            .otherSupportedModes(
                SemconvMode.V1_EXPERIMENTAL, SemconvMode.V1_EXPERIMENTAL.withDualEmit())
            .build());
  }

  SemconvMode messaging() {
    SemconvDomain.Builder domain =
        SemconvDomain.builder("messaging")
            .otherSupportedModes(
                SemconvMode.V1_EXPERIMENTAL, SemconvMode.V1_EXPERIMENTAL.withDualEmit());
    domain.defaultMode(v3Preview ? SemconvMode.V1_EXPERIMENTAL : SemconvMode.V0_STABLE);
    return resolveSemconvSelection(domain.build());
  }

  SemconvMode servicePeer() {
    return resolveSemconvSelection(
        SemconvDomain.builder("service.peer")
            .withoutStructuredConfig()
            .defaultMode(SemconvMode.V0_STABLE)
            .otherSupportedModes(
                SemconvMode.V1_EXPERIMENTAL, SemconvMode.V1_EXPERIMENTAL.withDualEmit())
            .build());
  }

  private SemconvMode resolveSemconvSelection(SemconvDomain domain) {
    SemconvMode semconvMode = resolveFromStructuredConfig(domain);
    if (semconvMode != null) {
      return semconvMode;
    }
    semconvMode = resolveFromFlags(domain);
    if (semconvMode != null) {
      return semconvMode;
    }
    return domain.defaultMode();
  }

  @Nullable
  private SemconvMode resolveFromStructuredConfig(SemconvDomain domain) {
    if (!domain.hasStructuredConfig()) {
      return null;
    }
    DeclarativeConfigProperties semconvConfig =
        structuredConfig.get(domain.configName()).get("semconv");
    Integer version = semconvConfig.getInt("version");
    if (version == null) {
      return null;
    }
    if (version == 0) {
      if (!domain.supportedModes().contains(SemconvMode.V0_STABLE)) {
        return null;
      }
      return SemconvMode.V0_STABLE;
    }
    if (version == 1) {
      SemconvMode requestedMode = requestedModeV1(semconvConfig, domain.supportedModes());
      if (requestedMode == null) {
        return null;
      }
      if (semconvConfig.getBoolean("dual_emit", false)
          && domain.supportedModes().contains(requestedMode.withDualEmit())) {
        return requestedMode.withDualEmit();
      }
      return requestedMode;
    }
    return null;
  }

  @Nullable
  private static SemconvMode requestedModeV1(
      DeclarativeConfigProperties semconvConfig, Set<SemconvMode> supportedModes) {
    if (semconvConfig.getBoolean("experimental", false)
        && supportedModes.contains(SemconvMode.V1_EXPERIMENTAL)) {
      return SemconvMode.V1_EXPERIMENTAL;
    }
    if (supportedModes.contains(SemconvMode.V1_STABLE)) {
      return SemconvMode.V1_STABLE;
    }
    return null;
  }

  @Nullable
  private SemconvMode resolveFromFlags(SemconvDomain domain) {
    SemconvMode flagTarget = flagTarget(domain.supportedModes());
    if (flagTarget == null) {
      return null;
    }
    Set<String> flags = flagsFor(domain.supportedModes());
    if (flags.contains(domain.flagKey()) || flags.contains(domain.flagKey() + "/dup")) {
      return fromFlags(domain.flagKey(), flags, flagTarget, domain.supportedModes());
    }
    return null;
  }

  @Nullable
  private static SemconvMode flagTarget(Set<SemconvMode> supportedModes) {
    if (supportedModes.contains(SemconvMode.V1_STABLE)) {
      return SemconvMode.V1_STABLE;
    }
    if (supportedModes.contains(SemconvMode.V1_EXPERIMENTAL)) {
      return SemconvMode.V1_EXPERIMENTAL;
    }
    return null;
  }

  private Set<String> flagsFor(Set<SemconvMode> supportedModes) {
    if (supportedModes.contains(SemconvMode.V1_EXPERIMENTAL)) {
      return effectivePreviewFlags();
    }
    return stableFlags;
  }

  private static SemconvMode fromFlags(
      String key, Set<String> values, SemconvMode targetMode, Set<SemconvMode> supportedModes) {
    if (values.contains(key + "/dup") && supportedModes.contains(targetMode.withDualEmit())) {
      return targetMode.withDualEmit();
    }
    return targetMode;
  }

  private Set<String> effectivePreviewFlags() {
    if (v3Preview) {
      return previewFlags;
    }
    return combine(stableFlags, previewFlags);
  }

  private static Set<String> resolveOptInValues(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig =
        SemconvStability.getGeneralInstrumentationConfig(openTelemetry);
    return resolveStringList(
        generalConfig.get("semconv_stability"), "opt_in", "otel.semconv-stability.opt-in");
  }

  private static Set<String> resolveStableOptInValues(
      OpenTelemetry openTelemetry, DeclarativeConfigProperties generalConfig) {
    return resolveStableOptInValues(generalConfig, resolveOptInValues(openTelemetry));
  }

  static Set<String> resolveStableOptInValues(
      DeclarativeConfigProperties generalConfig, Set<String> fallbackValues) {
    if (generalConfig.getPropertyKeys().contains("stability_opt_in_list")) {
      return resolveGeneralStableFlags(generalConfig);
    }
    return fallbackValues;
  }

  static Set<String> resolveGeneralStableFlags(DeclarativeConfigProperties generalConfig) {
    String value = generalConfig.getString("stability_opt_in_list");
    if (value == null || value.trim().isEmpty()) {
      return emptySet();
    }
    return parseCommaSeparatedSet(value);
  }

  private static Set<String> resolvePreviewValues(OpenTelemetry openTelemetry) {
    // preview is Java-specific, so it lives under java.common rather than general
    DeclarativeConfigProperties commonConfig =
        SemconvStability.getInstrumentationConfig(openTelemetry, "common");
    return resolveStringList(
        commonConfig.get("semconv_stability"), "preview", "otel.semconv-stability.preview");
  }

  private static Set<String> resolveStringList(
      DeclarativeConfigProperties config, String key, String fallbackProperty) {
    // Library instrumentation tests configure these modes using JVM system properties, so a direct
    // system-property fallback is needed.
    return resolveStringListWithFallbackValue(
        config, key, SystemProperty.getString(fallbackProperty));
  }

  static Set<String> resolveStringListWithFallbackValue(
      DeclarativeConfigProperties config, String key, @Nullable String fallbackValue) {
    if (config.getPropertyKeys().contains(key)) {
      return new HashSet<>(config.getScalarList(key, String.class, emptyList()));
    }
    return fallbackValue == null ? emptySet() : parseCommaSeparatedSet(fallbackValue);
  }

  static Set<String> parseCommaSeparatedSet(String value) {
    return asList(value.split(",")).stream()
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .collect(toSet());
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
