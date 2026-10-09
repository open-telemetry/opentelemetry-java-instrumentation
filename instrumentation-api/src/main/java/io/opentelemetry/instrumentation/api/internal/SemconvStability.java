/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  private static final boolean v3Preview;

  private static final boolean emitOldServicePeerSemconv;
  private static final boolean emitPreviewServicePeerSemconv;

  private static final boolean emitOldRpcSemconv;
  private static final boolean emitPreviewRpcSemconv;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.getOrNoop();
    DeclarativeConfigProperties generalConfig = getGeneralInstrumentationConfig(openTelemetry);
    v3Preview = v3Preview(openTelemetry);
    SemconvSelectionResolver semconvSelection =
        new SemconvSelectionResolver(openTelemetry, generalConfig);

    SemconvMode servicePeerSelection = semconvSelection.servicePeer();
    emitOldServicePeerSemconv = emitOld(servicePeerSelection);
    emitPreviewServicePeerSemconv = emitStable(servicePeerSelection);

    SemconvMode rpcSelection = semconvSelection.rpc();
    emitOldRpcSemconv = emitOld(rpcSelection);
    emitPreviewRpcSemconv = emitStable(rpcSelection);
  }

  public static boolean v3Preview(OpenTelemetry openTelemetry) {
    Boolean value = getInstrumentationConfig(openTelemetry, "common").getBoolean("v3_preview");
    if (value != null) {
      return value;
    }
    // Library instrumentation tests configure this mode using JVM system properties, so a direct
    // system-property fallback is needed.
    return SystemProperty.getBoolean("otel.instrumentation.common.v3-preview", false);
  }

  public static boolean v3Preview() { // to be removed in 3.0
    return v3Preview;
  }

  public static boolean emitOldServicePeerSemconv() {
    return emitOldServicePeerSemconv;
  }

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitOldServicePeerSemconv(OpenTelemetry openTelemetry) {
    return emitOld(selectionResolver(openTelemetry).servicePeer());
  }

  public static boolean emitPreviewServicePeerSemconv() {
    return emitPreviewServicePeerSemconv;
  }

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitPreviewServicePeerSemconv(OpenTelemetry openTelemetry) {
    return emitStable(selectionResolver(openTelemetry).servicePeer());
  }

  public static boolean emitOldRpcSemconv() {
    return emitOldRpcSemconv;
  }

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitOldRpcSemconv(OpenTelemetry openTelemetry) {
    return emitOld(selectionResolver(openTelemetry).rpc());
  }

  public static boolean emitPreviewRpcSemconv() {
    return emitPreviewRpcSemconv;
  }

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public static boolean emitPreviewRpcSemconv(OpenTelemetry openTelemetry) {
    return emitStable(selectionResolver(openTelemetry).rpc());
  }

  public static String rpcSchemaUrl() {
    return emitPreviewRpcSemconv ? SchemaUrls.V1_44_0 : SchemaUrls.V1_37_0;
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

  public static String messagingSchemaUrl() {
    return SchemaUrls.V1_43_0;
  }

  private SemconvStability() {}
}
