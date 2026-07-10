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
import java.util.HashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  private static final boolean v3Preview;

  private static final boolean emitOldDatabaseSemconv;
  private static final boolean emitStableDatabaseSemconv;

  private static final boolean emitOldCodeSemconv;
  private static final boolean emitStableCodeSemconv;

  private static final boolean emitOldServicePeerSemconv;
  private static final boolean emitStableServicePeerSemconv;

  private static final boolean emitOldRpcSemconv;
  private static final boolean emitStableRpcSemconv;

  private static final boolean emitOldMessagingSemconv;
  private static final boolean emitStableMessagingSemconv;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.getOrNoop();
    DeclarativeConfigProperties generalConfig = getGeneralInstrumentationConfig(openTelemetry);
    v3Preview = v3Preview(openTelemetry);
    SemconvSelectionResolver semconvSelection =
        new SemconvSelectionResolver(openTelemetry, generalConfig, v3Preview);

    SemconvMode databaseSelection = semconvSelection.database();
    emitOldDatabaseSemconv = emitOld(databaseSelection);
    emitStableDatabaseSemconv = emitStable(databaseSelection);

    SemconvMode codeSelection = semconvSelection.code();
    emitOldCodeSemconv = emitOld(codeSelection);
    emitStableCodeSemconv = emitStable(codeSelection);

    SemconvMode servicePeerSelection = semconvSelection.servicePeer();
    emitOldServicePeerSemconv = emitOld(servicePeerSelection);
    emitStableServicePeerSemconv = emitStable(servicePeerSelection);

    SemconvMode rpcSelection = semconvSelection.rpc();
    emitOldRpcSemconv = emitOld(rpcSelection);
    emitStableRpcSemconv = emitStable(rpcSelection);

    SemconvMode messagingSelection = semconvSelection.messaging();
    emitOldMessagingSemconv = emitOld(messagingSelection);
    emitStableMessagingSemconv = emitStable(messagingSelection);
  }

  @SuppressWarnings("deprecation") // using deprecated config property fallback
  public static boolean v3Preview(OpenTelemetry openTelemetry) {
    Boolean value = getInstrumentationConfig(openTelemetry, "common").getBoolean("v3_preview");
    if (value != null) {
      return value;
    }
    return ConfigPropertiesUtil.getBoolean("otel.instrumentation.common.v3-preview", false);
  }

  public static boolean v3Preview() { // to be removed in 3.0
    return v3Preview;
  }

  public static boolean emitOldDatabaseSemconv() { // to be removed in 3.0
    return emitOldDatabaseSemconv;
  }

  public static boolean emitStableDatabaseSemconv() { // to be removed in 3.0
    return emitStableDatabaseSemconv;
  }

  public static boolean emitOldServicePeerSemconv() {
    return emitOldServicePeerSemconv;
  }

  public static boolean emitStableServicePeerSemconv() {
    return emitStableServicePeerSemconv;
  }

  private static final Map<String, String> dbSystemNameMap = new HashMap<>();

  static {
    dbSystemNameMap.put("adabas", "softwareag.adabas");
    dbSystemNameMap.put("intersystems_cache", "intersystems.cache");
    dbSystemNameMap.put("cosmosdb", "azure.cosmosdb");
    dbSystemNameMap.put("db2", "ibm.db2");
    dbSystemNameMap.put("dynamodb", "aws.dynamodb");
    dbSystemNameMap.put("h2", "h2database");
    dbSystemNameMap.put("hanadb", "sap.hana");
    dbSystemNameMap.put("informix", "ibm.informix");
    dbSystemNameMap.put("ingres", "actian.ingres");
    dbSystemNameMap.put("maxdb", "sap.maxdb");
    dbSystemNameMap.put("mssql", "microsoft.sql_server");
    dbSystemNameMap.put("netezza", "ibm.netezza");
    dbSystemNameMap.put("oracle", "oracle.db");
    dbSystemNameMap.put("redshift", "aws.redshift");
    dbSystemNameMap.put("spanner", "gcp.spanner");
  }

  public static String stableDbSystemName(String oldDbSystem) {
    String dbSystemName = dbSystemNameMap.get(oldDbSystem);
    return dbSystemName != null ? dbSystemName : oldDbSystem;
  }

  public static boolean emitOldCodeSemconv() { // to be removed in 3.0
    return emitOldCodeSemconv;
  }

  public static boolean emitStableCodeSemconv() { // to be removed in 3.0
    return emitStableCodeSemconv;
  }

  public static boolean emitOldRpcSemconv() {
    return emitOldRpcSemconv;
  }

  public static boolean emitStableRpcSemconv() {
    return emitStableRpcSemconv;
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

  public static boolean emitOldMessagingSemconv() {
    return emitOldMessagingSemconv;
  }

  public static boolean emitStableMessagingSemconv() {
    return emitStableMessagingSemconv;
  }

  private SemconvStability() {}
}
