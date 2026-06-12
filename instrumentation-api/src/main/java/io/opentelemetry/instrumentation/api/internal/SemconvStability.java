/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

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
    v3Preview = resolveV3Preview(openTelemetry);
    Set<String> stabilityOptInValues = resolveStabilityOptInValues(generalConfig);
    Set<String> optInValues = resolveOptInValues(openTelemetry);
    Set<String> previewValues = resolvePreviewValues(openTelemetry);

    SemconvSelection databaseSelection =
        resolveSemconvSelection(
            generalConfig, "db", "database", v3Preview, stabilityOptInValues, optInValues);
    emitOldDatabaseSemconv = databaseSelection.emitOld();
    emitStableDatabaseSemconv = databaseSelection.emitStable();

    SemconvSelection codeSelection =
        resolveSemconvSelection(
            generalConfig, "code", "code", v3Preview, stabilityOptInValues, optInValues);
    emitOldCodeSemconv = codeSelection.emitOld();
    emitStableCodeSemconv = codeSelection.emitStable();

    emitOldServicePeerSemconv = shouldEmitOld("service.peer", v3Preview, previewValues);
    emitStableServicePeerSemconv = shouldEmitStable("service.peer", v3Preview, previewValues);

    SemconvSelection rpcSelection =
        resolveSemconvSelection(
            generalConfig, "rpc", "rpc", v3Preview, stabilityOptInValues, previewValues);
    emitOldRpcSemconv = rpcSelection.emitOld();
    emitStableRpcSemconv = rpcSelection.emitStable();

    SemconvSelection messagingSelection =
        resolveSemconvSelection(
            generalConfig, "messaging", "messaging", false, stabilityOptInValues, previewValues);
    emitOldMessagingSemconv = messagingSelection.emitOld();
    emitStableMessagingSemconv = messagingSelection.emitStable();
  }

  static Set<String> resolveStabilityOptInValues(DeclarativeConfigProperties generalConfig) {
    String value = generalConfig.getString("stability_opt_in_list");
    if (value == null || value.trim().isEmpty()) {
      return emptySet();
    }
    return asList(value.split(",")).stream()
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .collect(toSet());
  }

  static SemconvSelection resolveSemconvSelection(
      DeclarativeConfigProperties generalConfig,
      String domainConfigName,
      String legacyKey,
      boolean v3Preview,
      Set<String> stabilityOptInValues,
      Set<String> legacyFallbackValues) {
    SemconvSelection domainSelection =
        resolveDomainSemconvSelection(generalConfig, domainConfigName);
    if (domainSelection != null) {
      return domainSelection;
    }
    if (!stabilityOptInValues.isEmpty()) {
      return SemconvSelection.fromFallback(legacyKey, v3Preview, stabilityOptInValues);
    }
    return SemconvSelection.fromFallback(legacyKey, v3Preview, legacyFallbackValues);
  }

  @Nullable
  static SemconvSelection resolveDomainSemconvSelection(
      DeclarativeConfigProperties generalConfig, String domainConfigName) {
    DeclarativeConfigProperties semconvConfig = generalConfig.get(domainConfigName).get("semconv");
    Integer version = semconvConfig.getInt("version");
    if (version == null) {
      return null;
    }
    if (version == 0) {
      return new SemconvSelection(true, false);
    }
    if (version == 1) {
      return new SemconvSelection(semconvConfig.getBoolean("dual_emit", false), true);
    }
    return null;
  }

  @SuppressWarnings("deprecation") // using deprecated config property fallback
  private static boolean resolveV3Preview(OpenTelemetry openTelemetry) {
    Boolean value = getInstrumentationConfig(openTelemetry, "common").getBoolean("v3_preview");
    if (value != null) {
      return value;
    }
    return ConfigPropertiesUtil.getBoolean("otel.semconv-stability.v3-preview", false);
  }

  @SuppressWarnings("deprecation") // using deprecated config property fallback
  private static Set<String> resolveOptInValues(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties generalConfig = getGeneralInstrumentationConfig(openTelemetry);
    Set<String> values =
        new HashSet<>(
            generalConfig
                .get("semconv_stability")
                .getScalarList("opt_in", String.class, new ArrayList<>()));
    if (values.isEmpty()) {
      String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
      if (value != null) {
        return new HashSet<>(asList(value.split(",")));
      }
    }
    return values;
  }

  @SuppressWarnings("deprecation") // using deprecated config property fallback
  private static Set<String> resolvePreviewValues(OpenTelemetry openTelemetry) {
    // preview is Java-specific, so it lives under java.common rather than general
    DeclarativeConfigProperties commonConfig = getInstrumentationConfig(openTelemetry, "common");
    Set<String> values =
        new HashSet<>(
            commonConfig
                .get("semconv_stability")
                .getScalarList("preview", String.class, new ArrayList<>()));
    if (values.isEmpty()) {
      String value = ConfigPropertiesUtil.getString("otel.semconv-stability.preview");
      if (value != null) {
        return new HashSet<>(asList(value.split(",")));
      }
    }
    return values;
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

  public static boolean emitOldMessagingSemconv() {
    return emitOldMessagingSemconv;
  }

  public static boolean emitStableMessagingSemconv() {
    return emitStableMessagingSemconv;
  }

  static final class SemconvSelection {
    private final boolean emitOld;
    private final boolean emitStable;

    private SemconvSelection(boolean emitOld, boolean emitStable) {
      this.emitOld = emitOld;
      this.emitStable = emitStable;
    }

    static SemconvSelection fromFallback(String key, boolean v3Preview, Set<String> values) {
      return new SemconvSelection(
          shouldEmitOld(key, v3Preview, values), shouldEmitStable(key, v3Preview, values));
    }

    boolean emitOld() {
      return emitOld;
    }

    boolean emitStable() {
      return emitStable;
    }
  }

  private SemconvStability() {}
}
