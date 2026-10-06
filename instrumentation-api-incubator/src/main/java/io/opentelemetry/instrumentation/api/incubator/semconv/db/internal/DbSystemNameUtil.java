/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbSystemNameUtil {

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

  public static String normalizeDbSystemName(String dbSystemName) {
    String normalizedDbSystemName = dbSystemNameMap.get(dbSystemName);
    return normalizedDbSystemName != null ? normalizedDbSystemName : dbSystemName;
  }

  private DbSystemNameUtil() {}
}
