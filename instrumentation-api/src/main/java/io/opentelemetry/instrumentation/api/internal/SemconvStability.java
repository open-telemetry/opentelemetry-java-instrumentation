/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Arrays.asList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@SuppressWarnings("deprecation")
public final class SemconvStability {

  private static final boolean emitOldDatabaseSemconv;
  private static final boolean emitStableDatabaseSemconv;

  private static final boolean emitOldCodeSemconv;
  private static final boolean emitStableCodeSemconv;

  private static final boolean emitOldServicePeerSemconv;
  private static final boolean emitStableServicePeerSemconv;

  private static final boolean emitOldRpcSemconv;
  private static final boolean emitStableRpcSemconv;

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    boolean v3Preview =
        getInstrumentationConfig(openTelemetry, "common").getBoolean("v3_preview", false);
    Set<String> optInValues = resolveOptInValues(openTelemetry);

    emitOldDatabaseSemconv = shouldEmitOld("database", v3Preview, optInValues);
    emitStableDatabaseSemconv = shouldEmitStable("database", v3Preview, optInValues);

    emitOldCodeSemconv = shouldEmitOld("code", v3Preview, optInValues);
    emitStableCodeSemconv = shouldEmitStable("code", v3Preview, optInValues);

    emitOldServicePeerSemconv = shouldEmitOld("service.peer", v3Preview, optInValues);
    emitStableServicePeerSemconv = shouldEmitStable("service.peer", v3Preview, optInValues);

    emitOldRpcSemconv = shouldEmitOld("rpc", v3Preview, optInValues);
    emitStableRpcSemconv = shouldEmitStable("rpc", v3Preview, optInValues);
  }

  private static Set<String> resolveOptInValues(OpenTelemetry openTelemetry) {
    // Try declarative config via GlobalOpenTelemetry first
    DeclarativeConfigProperties generalConfig = getGeneralInstrumentationConfig(openTelemetry);
    Set<String> values =
        new HashSet<>(
            generalConfig
                .get("semconv_stability")
                .getScalarList("opt_in", String.class, new ArrayList<>()));
    if (values.isEmpty()) {
      // Fall back to system property / env var
      String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
      if (value != null) {
        return new HashSet<>(asList(value.split(",")));
      }
    }
    return values;
  }

  public static boolean emitOldDatabaseSemconv() {
    return emitOldDatabaseSemconv;
  }

  public static boolean emitStableDatabaseSemconv() {
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

  public static boolean emitOldCodeSemconv() {
    return emitOldCodeSemconv;
  }

  public static boolean emitStableCodeSemconv() {
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

  private static boolean shouldEmitOld(String key, boolean v3Preview, Set<String> optInValues) {
    if (v3Preview) {
      return false;
    }
    if (optInValues.contains(key + "/dup")) {
      return true;
    }
    return !optInValues.contains(key);
  }

  private static boolean shouldEmitStable(String key, boolean v3Preview, Set<String> optInValues) {
    if (v3Preview) {
      return true;
    }
    return optInValues.contains(key) || optInValues.contains(key + "/dup");
  }

  private static DeclarativeConfigProperties getGeneralInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getGeneralInstrumentationConfig()
        : empty();
  }

  private static DeclarativeConfigProperties getInstrumentationConfig(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getInstrumentationConfig(instrumentationName)
        : empty();
  }

  private SemconvStability() {}
}
