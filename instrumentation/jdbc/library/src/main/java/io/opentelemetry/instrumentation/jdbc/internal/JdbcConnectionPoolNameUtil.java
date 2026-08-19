/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils;
import java.util.Properties;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcConnectionPoolNameUtil {

  public static String poolName(Properties properties, String fallbackName) {
    DbInfo.Builder dbInfoBuilder = DbInfo.builder();

    String serverName = getPropertyValue(properties, "serverName");
    if (serverName != null && !serverName.isEmpty()) {
      dbInfoBuilder.serverAddress(UrlParsingUtils.stripIpv6Brackets(serverName));
    }

    Integer serverPort = UrlParsingUtils.parsePort(getPropertyValue(properties, "portNumber"));
    if (serverPort != null) {
      dbInfoBuilder.serverPort(serverPort);
    }

    String databaseName = getPropertyValue(properties, "databaseName");
    if (databaseName != null && !databaseName.isEmpty()) {
      dbInfoBuilder.dbNamespace(databaseName);
    }

    return poolName(dbInfoBuilder.build(), fallbackName);
  }

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

  @Nullable
  private static String getPropertyValue(Properties properties, String name) {
    Object value = properties.get(name);
    return value == null ? properties.getProperty(name) : value.toString();
  }

  private JdbcConnectionPoolNameUtil() {}
}
