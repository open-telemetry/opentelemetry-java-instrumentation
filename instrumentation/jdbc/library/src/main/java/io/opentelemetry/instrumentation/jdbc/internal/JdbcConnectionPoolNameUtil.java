/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcConnectionPoolNameUtil {

  public static String poolName(DbInfo dbInfo, String fallbackName) {
    String serverAddress = dbInfo.getServerAddress();
    Integer serverPort = dbInfo.getServerPort();
    String dbNamespace = dbInfo.getDbNamespace();

    StringBuilder poolName = new StringBuilder();
    if (serverAddress != null) {
      if (serverAddress.indexOf(':') >= 0) {
        poolName.append('[').append(serverAddress).append(']');
      } else {
        poolName.append(serverAddress);
      }
      if (serverPort != null) {
        poolName.append(':').append(serverPort);
      }
    }
    if (dbNamespace != null) {
      if (poolName.length() > 0) {
        poolName.append('/');
      }
      poolName.append(dbNamespace);
    }

    // Do not append a sequence suffix: it would be unstable across restarts and nodes.
    // Asynchronous metric observations with equal attributes are spatially aggregated, so pools
    // connected to the same database can intentionally share the derived name.
    return poolName.length() > 0 ? poolName.toString() : fallbackName;
  }

  private JdbcConnectionPoolNameUtil() {}
}
