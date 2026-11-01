/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitOldServicePeerSemconv(OpenTelemetry openTelemetry) {
    return emitOld(selectionResolver(openTelemetry).servicePeer());
  }

  public static boolean emitPreviewServicePeerSemconv(OpenTelemetry openTelemetry) {
    return emitStable(selectionResolver(openTelemetry).servicePeer());
  }

  private static SemconvSelectionResolver selectionResolver(OpenTelemetry openTelemetry) {
    return new SemconvSelectionResolver(openTelemetry);
  }

  static DeclarativeConfigProperties getInstrumentationConfig(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getInstrumentationConfig(instrumentationName)
        : empty();
  }

  private static boolean emitOld(SemconvMode mode) {
    return mode.version() == 0 || mode.dualEmit();
  }

  private static boolean emitStable(SemconvMode mode) {
    return mode.version() >= 1;
  }

  private SemconvStability() {}
}
