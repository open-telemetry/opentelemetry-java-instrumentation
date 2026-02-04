/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.HostPort;

/**
 * Parser for Microsoft SQL Server JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>sqlserver://host:1433;databaseName=db
 *   <li>sqlserver://host\instance
 *   <li>sqlserver://host:1433/db
 *   <li>microsoft:sqlserver://host:1433
 *   <li>sqlserver://[::1]:1433 (IPv6)
 *   <li>sqlserver://[::1]\instance (IPv6 with instance)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class MssqlUrlParser implements JdbcUrlParser {

  // copied from DbAttributes.DbSystemNameValues
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String MSSQL = "mssql";

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 1433;

  public static final MssqlUrlParser INSTANCE = new MssqlUrlParser();

  private MssqlUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(MICROSOFT_SQL_SERVER);
    ctx.oldSemconvSystem(MSSQL);
    ctx.host(DEFAULT_HOST);
    ctx.port(DEFAULT_PORT);

    // Extract subtype from URL like microsoft:sqlserver://...
    String subtype = UrlParsingUtils.extractSubtype(jdbcUrl);
    if (subtype != null) {
      ctx.subtype(subtype);
    }

    // Layer 3: URL params (SQL Server-specific: servername)
    ctx.applyCommonParams(jdbcUrl, ";", ";");

    // Layer 4: Parse URL structure (host:port/path)
    parseUrlWithInstance(jdbcUrl, ctx);

    // DataSource properties applied last — SQL Server driver gives DataSource precedence over URL
    ctx.applyDataSourceProperties();
  }

  /**
   * Parse URL with SQL Server-specific backslash instance handling.
   *
   * <p>Uses the current ctx.host() value (from params/DataSource properties) as a fallback when the
   * URL doesn't contain a host part.
   *
   * @param jdbcUrl the JDBC URL
   * @param ctx the parse context to update
   */
  private static void parseUrlWithInstance(String jdbcUrl, ParseContext ctx) {
    // Capture current host as fallback (could be from defaults, DataSource properties, or URL
    // params)
    String fallbackHost = ctx.host() != null ? ctx.host() : "";
    // Split off semicolon-delimited parameters
    String urlPart = jdbcUrl.split(";", 2)[0];

    int hostIndex = urlPart.indexOf("://");
    String serverName;

    if (hostIndex <= 0) {
      // No URL host - use fallback
      if (fallbackHost.isEmpty()) {
        return;
      }
      serverName = fallbackHost;
    } else {
      // URL host takes precedence over fallback
      String urlServerName = urlPart.substring(hostIndex + 3);
      serverName = urlServerName.isEmpty() ? fallbackHost : urlServerName;
      if (serverName.isEmpty()) {
        return;
      }
    }

    // Extract database path from URL (before handling instance)
    // Fallback only: ;databaseName= param (from applyCommonParams) takes precedence over URL path.
    // The MSSQL JDBC URL grammar does not include a path component for database name — it uses
    // ;databaseName= as a semicolon-delimited property instead.
    // See https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url
    int pathLoc = serverName.indexOf("/");
    if (pathLoc > 0) {
      String databaseName = serverName.substring(pathLoc + 1);
      if (ctx.databaseName() == null && !databaseName.isEmpty()) {
        ctx.databaseName(databaseName);
      }
      serverName = serverName.substring(0, pathLoc);
    }

    String instanceName = null;

    // Handle IPv6 addresses and extract host:port
    HostPort hostPort = UrlParsingUtils.extractHostPort(serverName);
    serverName = hostPort.host();
    if (hostPort.port() != null) {
      ctx.port(hostPort.port());
    }

    // SQL Server-specific: Extract backslash-separated instance name (host\instance)
    int instanceLoc = serverName.indexOf("\\");
    if (instanceLoc > 0) {
      if (hostPort.ipv6Address() != null) {
        // For IPv6 with instance: [2001:db8::1]\INSTANCE
        // Extract instance name between backslash and closing bracket
        int closingBracket = serverName.lastIndexOf(']');
        if (closingBracket > instanceLoc) {
          instanceName = serverName.substring(instanceLoc + 1, closingBracket);
          // Reconstruct host with just the IPv6 address
          serverName = "[" + hostPort.ipv6Address() + "]";
        }
      } else {
        instanceName = serverName.substring(instanceLoc + 1);
        serverName = serverName.substring(0, instanceLoc);
      }
    }

    if (!serverName.isEmpty()) {
      ctx.host(serverName);
    }

    // Set namespace with instance formatting
    setNamespace(ctx, instanceName);
  }

  /**
   * Sets the namespace after parsing completes.
   *
   * <p>For SQL Server, namespace format is:
   *
   * <ul>
   *   <li>{@code instance|database} when both named instance and database are present
   *   <li>{@code instance} when only instance is present (with or without explicit DatabaseName
   *       parameter)
   * </ul>
   */
  @SuppressWarnings("deprecation") // dbName is deprecated, to be removed in 3.0
  private static void setNamespace(ParseContext ctx, String instanceName) {
    String database = ctx.databaseName();

    // When we have an instance name
    if (instanceName != null && !instanceName.isEmpty()) {
      // Preserve old behavior: dbName is the instance name (not the database name)
      ctx.dbName(instanceName);
      // If there's a non-empty database that's different from instance: format as instance|database
      if (database != null && !database.isEmpty() && !database.equals(instanceName)) {
        ctx.namespace(instanceName + "|" + database);
      } else {
        ctx.namespace(instanceName);
      }
    }
  }
}
