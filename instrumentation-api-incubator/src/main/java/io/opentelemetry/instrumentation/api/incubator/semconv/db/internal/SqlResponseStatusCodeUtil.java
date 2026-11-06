/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MARIADB;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqlResponseStatusCodeUtil {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_DB2 = "ibm.db2";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String SQLITE = "sqlite";

  @Nullable
  public static String getResponseStatusCode(
      @Nullable String dbSystemName, @Nullable String sqlState, @Nullable String vendorCode) {
    if (sqlState != null && sqlState.isEmpty()) {
      sqlState = null;
    }
    if (vendorCode != null && vendorCode.isEmpty()) {
      vendorCode = null;
    }

    if (prefersVendorCode(dbSystemName) && vendorCode != null) {
      return vendorCode;
    }
    if (POSTGRESQL.equals(dbSystemName) && sqlState != null) {
      return sqlState;
    }
    if (sqlState != null && vendorCode != null) {
      return sqlState + "/" + vendorCode;
    }
    return sqlState != null ? sqlState : vendorCode;
  }

  private static boolean prefersVendorCode(@Nullable String dbSystemName) {
    return IBM_DB2.equals(dbSystemName)
        || MARIADB.equals(dbSystemName)
        || MICROSOFT_SQL_SERVER.equals(dbSystemName)
        || MYSQL.equals(dbSystemName)
        || ORACLE_DB.equals(dbSystemName)
        || SQLITE.equals(dbSystemName);
  }

  private SqlResponseStatusCodeUtil() {}
}
