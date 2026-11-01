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
  // Preview flags for service.peer. Reads through OpenTelemetry-backed
  // java.common.semconv_stability.preview config, and falls back to SystemProperty for
  // otel.semconv-stability.preview in library instrumentation.
  private final Set<String> previewFlags;

  SemconvSelectionResolver(OpenTelemetry openTelemetry) {
    this.previewFlags = resolvePreviewValues(openTelemetry);
  }

  SemconvMode servicePeer() {
    if (previewFlags.contains("service.peer/dup")) {
      return SemconvMode.V1_EXPERIMENTAL.withDualEmit();
    }
    return previewFlags.contains("service.peer")
        ? SemconvMode.V1_EXPERIMENTAL
        : SemconvMode.V0_STABLE;
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
}
