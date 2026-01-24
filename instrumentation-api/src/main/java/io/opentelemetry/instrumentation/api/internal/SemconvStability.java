/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  // copied from RpcIncubatingAttributes
  private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc.method");

  // virtual key to avoid clash with stable rpc.method
  private static final AttributeKey<String> RPC_METHOD_OLD =
      AttributeKey.stringKey("rpc.method.deprecated");

  private static final boolean emitOldDatabaseSemconv;
  private static final boolean emitStableDatabaseSemconv;

  private static final boolean emitOldCodeSemconv;
  private static final boolean emitStableCodeSemconv;

  private static final boolean emitOldRpcSemconv;
  private static final boolean emitStableRpcSemconv;

  static {
    boolean oldDatabase = true;
    boolean stableDatabase = false;

    boolean oldCode = true;
    boolean stableCode = false;

    boolean oldRpc = true;
    boolean stableRpc = false;

    String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
    if (value != null) {
      Set<String> values = new HashSet<>(asList(value.split(",")));

      // no else -- technically it's possible to set "XXX,XXX/dup", in which case we
      // should emit both sets of attributes for XXX

      if (values.contains("database")) {
        oldDatabase = false;
        stableDatabase = true;
      }
      if (values.contains("database/dup")) {
        oldDatabase = true;
        stableDatabase = true;
      }

      if (values.contains("code")) {
        oldCode = false;
        stableCode = true;
      }
      if (values.contains("code/dup")) {
        oldCode = true;
        stableCode = true;
      }

      if (values.contains("rpc")) {
        oldRpc = false;
        stableRpc = true;
      }
      if (values.contains("rpc/dup")) {
        oldRpc = true;
        stableRpc = true;
      }
    }

    emitOldDatabaseSemconv = oldDatabase;
    emitStableDatabaseSemconv = stableDatabase;

    emitOldCodeSemconv = oldCode;
    emitStableCodeSemconv = stableCode;

    emitOldRpcSemconv = oldRpc;
    emitStableRpcSemconv = stableRpc;
  }

  public static boolean emitOldDatabaseSemconv() {
    return emitOldDatabaseSemconv;
  }

  public static boolean emitStableDatabaseSemconv() {
    return emitStableDatabaseSemconv;
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

  public static boolean isEmitOldCodeSemconv() {
    return emitOldCodeSemconv;
  }

  public static boolean isEmitStableCodeSemconv() {
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

  public static AttributeKey<String> getOldRpcMethodAttributeKey() {
    if (emitStableRpcSemconv()) {
      // to avoid clash when both semconv are emitted
      return RPC_METHOD_OLD;
    }
    return RPC_METHOD;
  }

  private SemconvStability() {}

  public static Attributes getOldRpcMetricAttributes(Attributes attributes) {
    if (emitStableRpcSemconv()) {
      // need to copy attributes
      return attributes.toBuilder().put(RPC_METHOD, attributes.get(RPC_METHOD_OLD)).build();
    }
    return attributes;
  }
}
