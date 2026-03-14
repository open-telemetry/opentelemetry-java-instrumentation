/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

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
public final class SemconvStability {

  private static boolean emitOldDatabaseSemconv;
  private static boolean emitStableDatabaseSemconv;

  private static boolean emitOldCodeSemconv;
  private static boolean emitStableCodeSemconv;

  private static boolean emitOldServicePeerSemconv;
  private static boolean emitStableServicePeerSemconv;

  private static boolean emitOldRpcSemconv;
  private static boolean emitStableRpcSemconv;

  private static boolean emitGenAiLatestExperimentalSemconv;

  static {
    String value = System.getProperty("otel.semconv-stability.opt-in");
    if (value == null) {
      value = System.getenv("OTEL_SEMCONV_STABILITY_OPT_IN");
    }
    Set<String> values = value != null ? new HashSet<>(asList(value.split(","))) : emptySet();
    configure(values);
  }

  private static void configure(Set<String> values) {
    // set default values
    emitOldDatabaseSemconv = true;
    emitStableDatabaseSemconv = false;

    emitOldCodeSemconv = true;
    emitStableCodeSemconv = false;

    emitOldServicePeerSemconv = true;
    emitStableServicePeerSemconv = false;

    emitOldRpcSemconv = true;
    emitStableRpcSemconv = false;

    emitGenAiLatestExperimentalSemconv = false;

    // no else -- technically it's possible to set "XXX,XXX/dup", in which case we
    // should emit both sets of attributes for XXX

    if (values.contains("database")) {
      emitOldDatabaseSemconv = false;
      emitStableDatabaseSemconv = true;
    }
    if (values.contains("database/dup")) {
      emitOldDatabaseSemconv = true;
      emitStableDatabaseSemconv = true;
    }

    if (values.contains("code")) {
      emitOldCodeSemconv = false;
      emitStableCodeSemconv = true;
    }
    if (values.contains("code/dup")) {
      emitOldCodeSemconv = true;
      emitStableCodeSemconv = true;
    }

    if (values.contains("service.peer")) {
      emitOldServicePeerSemconv = false;
      emitStableServicePeerSemconv = true;
    }
    if (values.contains("service.peer/dup")) {
      emitOldServicePeerSemconv = true;
      emitStableServicePeerSemconv = true;
    }

    if (values.contains("rpc")) {
      emitOldRpcSemconv = false;
      emitStableRpcSemconv = true;
    }
    if (values.contains("rpc/dup")) {
      emitOldRpcSemconv = true;
      emitStableRpcSemconv = true;
    }

    if (values.contains("gen_ai_latest_experimental")) {
      emitGenAiLatestExperimentalSemconv = true;
    }
  }

  public static void configure(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig = getGeneralInstrumentationConfig(openTelemetry);

    Set<String> values =
        new HashSet<>(
            generalConfig
                .get("semconv_stability")
                .getScalarList("opt_in", String.class, new ArrayList<>()));
    configure(values);
  }

  private static DeclarativeConfigProperties getGeneralInstrumentationConfig(
      OpenTelemetry openTelemetry) {
    return openTelemetry instanceof ExtendedOpenTelemetry
        ? ((ExtendedOpenTelemetry) openTelemetry).getGeneralInstrumentationConfig()
        : empty();
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

  public static boolean emitOldGenAiSemconv() {
    return !emitGenAiLatestExperimentalSemconv;
  }

  public static boolean emitGenAiLatestExperimentalSemconv() {
    return emitGenAiLatestExperimentalSemconv;
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

  private SemconvStability() {}
}
