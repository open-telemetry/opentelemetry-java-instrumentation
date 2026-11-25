/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  private static final String PREVIEW_PROPERTY = "otel.semconv-stability.preview";

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitPreviewGenAiSemconv(OpenTelemetry openTelemetry) {
    return previewDomains(openTelemetry).contains("gen_ai");
  }

  private static Set<String> previewDomains(OpenTelemetry openTelemetry) {
    // preview is Java-specific, so it lives under java.common rather than general
    DeclarativeConfigProperties semconvStability =
        commonInstrumentationConfig(openTelemetry).get("semconv_stability");
    // Library instrumentation tests configure this mode using JVM system properties, so a direct
    // system-property fallback is needed.
    return resolvePreviewDomains(semconvStability, SystemProperty.getString(PREVIEW_PROPERTY));
  }

  private static Set<String> resolvePreviewDomains(
      DeclarativeConfigProperties semconvStability, @Nullable String fallbackValue) {
    if (semconvStability.getPropertyKeys().contains("preview")) {
      return new HashSet<>(semconvStability.getScalarList("preview", String.class, emptyList()));
    }
    if (fallbackValue == null) {
      return new HashSet<>();
    }
    return Arrays.stream(fallbackValue.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(toSet());
  }

  private static DeclarativeConfigProperties commonInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getInstrumentationConfig("common")
        : empty();
  }

  private SemconvStability() {}
}
