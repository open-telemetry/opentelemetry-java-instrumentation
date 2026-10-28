/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.HashMap;
import java.util.Map;

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

  public static boolean emitOldRpcSemconv(OpenTelemetry openTelemetry) {
    return emitOld(selectionResolver(openTelemetry).rpc());
  }

  public static boolean emitPreviewRpcSemconv(OpenTelemetry openTelemetry) {
    return emitStable(selectionResolver(openTelemetry).rpc());
  }

  private static SemconvSelectionResolver selectionResolver(OpenTelemetry openTelemetry) {
    return new SemconvSelectionResolver(
        openTelemetry, getGeneralInstrumentationConfig(openTelemetry));
  }

  private static final Map<String, String> rpcSystemNameMap = new HashMap<>();

  static {
    rpcSystemNameMap.put("apache_dubbo", "dubbo");
    rpcSystemNameMap.put("connect_rpc", "connectrpc");
  }

  public static String stableRpcSystemName(String oldRpcSystem) {
    String rpcSystemName = rpcSystemNameMap.get(oldRpcSystem);
    return rpcSystemName != null ? rpcSystemName : oldRpcSystem;
  }

  static DeclarativeConfigProperties getGeneralInstrumentationConfig(OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getGeneralInstrumentationConfig()
        : empty();
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
