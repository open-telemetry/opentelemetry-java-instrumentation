/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
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
    return poolName(dbInfo(properties), fallbackName);
  }

  public static String poolName(DbInfo dbInfo, String fallbackName) {
    if (emitStableDatabaseSemconv()) {
      String dbNamespace = dbInfo.getDbNamespace();
      if (dbNamespace != null && !dbNamespace.isEmpty()) {
        return dbNamespace;
      }

      DbServerTarget target = dbInfo.getConfiguredServerTarget();
      if (target != null && !target.getAddress().isEmpty()) {
        return endpoint(target.getAddress(), target.getPort());
      }

      String dbSystemName = dbInfo.getDbSystemName();
      if (dbSystemName != null && !dbSystemName.isEmpty()) {
        return dbSystemName;
      }

      return fallbackName;
    }

    String serverAddress = dbInfo.getLegacyServerAddress();
    Integer serverPort = dbInfo.getLegacyServerPort();
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

  public static DbInfo dbInfo(Properties properties) {
    DbInfo.Builder dbInfoBuilder = DbInfo.builder();

    String serverName = getPropertyValue(properties, "serverName");
    if (serverName != null && !serverName.isEmpty()) {
      serverName = UrlParsingUtils.stripIpv6Brackets(serverName);
      dbInfoBuilder.legacyServerAddress(serverName);
    }

    Integer serverPort = UrlParsingUtils.parsePort(getPropertyValue(properties, "portNumber"));
    if (serverPort != null) {
      dbInfoBuilder.legacyServerPort(serverPort);
    }

    if (serverName != null && !serverName.isEmpty()) {
      dbInfoBuilder.configuredServerTarget(DbServerTarget.create(serverName, serverPort));
    }

    String databaseName = getPropertyValue(properties, "databaseName");
    if (databaseName != null && !databaseName.isEmpty()) {
      dbInfoBuilder.dbNamespace(databaseName);
    }

    return dbInfoBuilder.build();
  }

  public static Attributes databaseAttributes(DbInfo dbInfo) {
    if (!emitStableDatabaseSemconv()) {
      return Attributes.empty();
    }

    AttributesBuilder attributes = Attributes.builder();
    attributes.put(DB_SYSTEM_NAME, dbInfo.getDbSystemName());
    attributes.put(DB_NAMESPACE, dbInfo.getDbNamespace());
    DbServerTarget target = dbInfo.getConfiguredServerTarget();
    if (target != null) {
      attributes.put(SERVER_ADDRESS, target.getAddress());
      Integer port = target.getPort();
      attributes.put(SERVER_PORT, port == null ? null : port.longValue());
    }
    return attributes.build();
  }

  private static String endpoint(String address, @Nullable Integer port) {
    if (address.indexOf(',') >= 0) {
      return address;
    }

    StringBuilder endpoint = new StringBuilder();
    if (address.indexOf(':') >= 0) {
      endpoint.append('[').append(address).append(']');
    } else {
      endpoint.append(address);
    }
    if (port != null) {
      endpoint.append(':').append(port);
    }
    return endpoint.toString();
  }

  @Nullable
  private static String getPropertyValue(Properties properties, String name) {
    Object value = properties.get(name);
    return value == null ? properties.getProperty(name) : value.toString();
  }

  private JdbcConnectionPoolNameUtil() {}
}
