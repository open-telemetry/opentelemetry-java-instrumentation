/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.splitQuery;

import java.util.Map;

/**
 * Parser for Apache Derby JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>derby:mydb (directory, default)
 *   <li>derby:directory:/path/to/db
 *   <li>derby:memory:testdb (in-memory)
 *   <li>derby:classpath:db (from classpath)
 *   <li>derby:jar:(path/to/db.jar)db
 *   <li>derby://host:1527/db (network)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class DerbyUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String DERBY = "derby";

  private static final String DEFAULT_USER = "APP";
  private static final int DEFAULT_PORT = 1527;
  private static final String[] SIMPLE_MODES = {"memory", "classpath", "jar"};

  public static final DerbyUrlParser INSTANCE = new DerbyUrlParser();

  private DerbyUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(DERBY);
    ctx.user(DEFAULT_USER);

    ctx.applyUserProperty();

    // Parse URL
    String derbyUrl = jdbcUrl.substring("derby:".length());
    String[] split = derbyUrl.split(";", 2);

    String details = split[0];

    // Parse URL path first — Derby gives subname (path) priority over ;databaseName= attribute.
    // See https://db.apache.org/derby/docs/10.8/ref/rrefattrib17246.html
    parseDetails(details, ctx);

    if (split.length > 1) {
      // Apply semicolon-delimited params; databaseName is fallback only (path takes priority)
      applyParamsAsFallback(split[1], ctx);
    }
  }

  /**
   * Apply semicolon-delimited URL parameters, with databaseName as fallback only.
   *
   * <p>Derby gives the subname (path) priority over the databaseName attribute, so we only set
   * databaseName from params when no path was present.
   */
  private static void applyParamsAsFallback(String paramString, ParseContext ctx) {
    Map<String, String> params = splitQuery(paramString, ";");
    if (params.containsKey("user")) {
      ctx.user(params.get("user"));
    }
    // databaseName attribute is fallback only — subname (path) takes priority
    if (ctx.databaseName() == null) {
      String databaseName = params.get("databasename");
      if (databaseName != null && !databaseName.isEmpty()) {
        ctx.databaseName(databaseName);
      }
    }
  }

  private static void parseDetails(String details, ParseContext ctx) {
    // Handle network mode (starts with //)
    if (details.startsWith("//")) {
      parseNetworkMode(details, ctx);
      ctx.subtype("network");
      return;
    }

    // Local modes: host/port don't apply — clear any values set by DataSource properties
    ctx.host(null);
    ctx.port(null);

    // Handle directory mode specially (uses parseDirectoryName)
    if (details.startsWith("directory:")) {
      String databaseName = parseDirectoryName(details.substring("directory:".length()));
      if (databaseName != null && !databaseName.isEmpty()) {
        ctx.databaseName(databaseName);
      }
      ctx.subtype("directory");
      return;
    }

    // Handle simple prefix modes: memory, classpath, jar
    for (String mode : SIMPLE_MODES) {
      String prefix = mode + ":";
      if (details.startsWith(prefix)) {
        String databaseName = details.substring(prefix.length());
        if (!databaseName.isEmpty()) {
          ctx.databaseName(databaseName);
        }
        ctx.subtype(mode);
        return;
      }
    }

    // Default to directory
    if (!details.isEmpty()) {
      ctx.databaseName(details);
    }
    ctx.subtype("directory");
  }

  private static String parseDirectoryName(String urlInstance) {
    if (!urlInstance.isEmpty()) {
      int dbNameStartLocation = urlInstance.lastIndexOf('/');
      if (dbNameStartLocation != -1) {
        return urlInstance.substring(dbNameStartLocation + 1);
      }
      return urlInstance;
    }
    return null;
  }

  private static void parseNetworkMode(String details, ParseContext ctx) {
    String url = details.substring("//".length());

    int instanceLoc = url.indexOf("/");
    if (instanceLoc >= 0) {
      String databaseName = url.substring(instanceLoc + 1);
      int protoLoc = databaseName.indexOf(":");
      if (protoLoc >= 0) {
        databaseName = databaseName.substring(protoLoc + 1);
      }
      // Path takes priority over ;databaseName= param
      ctx.databaseName(databaseName);
      url = url.substring(0, instanceLoc);
    }

    int portLoc = url.indexOf(":");
    if (portLoc > 0) {
      ctx.host(url.substring(0, portLoc));
      Integer port = parsePort(url.substring(portLoc + 1));
      if (port != null) {
        ctx.port(port);
      }
    } else {
      ctx.host(url);
      ctx.port(DEFAULT_PORT);
    }
  }
}
