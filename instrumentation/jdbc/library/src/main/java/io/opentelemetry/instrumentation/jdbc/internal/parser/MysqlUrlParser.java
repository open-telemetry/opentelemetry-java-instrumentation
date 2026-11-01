/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractSubtype;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.indexOfAny;
import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.jdbc.internal.parser.MysqlHostParser.HostList;
import io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.HostPort;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Parser for MySQL and MariaDB JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>mysql://host:3306/db
 *   <li>mysql://host/db?user=root
 *   <li>mysql:aurora://host:3306/db
 *   <li>mysql:host:3306/db (non-standard format)
 *   <li>mariadb:replication://host1,host2/db
 *   <li>mariadb:sequential:address=(host=host1)(port=3306)(user=root)/db
 *   <li>mysql://address=(host=host1)(port=3306)/db
 *   <li>mysql://(host=host1,port=3306)/db
 *   <li>mysql://[::1]:3306/db (IPv6)
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MysqlUrlParser implements JdbcUrlParser {

  private static final Logger logger = Logger.getLogger(MysqlUrlParser.class.getName());

  // copied from DbAttributes.DbSystemNameValues
  private static final String MYSQL = "mysql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MARIADB = "mariadb";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private static final String DEFAULT_HOST = "localhost";
  private static final int DEFAULT_PORT = 3306;

  public static final MysqlUrlParser INSTANCE = new MysqlUrlParser();

  private MysqlUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    String system = ctx.type();
    if (!MYSQL.equals(system) && !MARIADB.equals(system)) {
      system = OTHER_SQL;
    }
    ctx.system(system);
    ctx.defaultHost(DEFAULT_HOST);
    ctx.defaultPort(DEFAULT_PORT);
    ctx.applyUserProperty();

    if (jdbcUrl.indexOf("://") > 0) {
      parseUrl(jdbcUrl, ctx, system.equals(MYSQL));
    } else {
      parseNonStandardUrl(jdbcUrl.toLowerCase(Locale.ROOT), ctx);
    }
  }

  private static void parseUrl(String jdbcUrl, ParseContext ctx, boolean mysql) {
    int hostsStart = jdbcUrl.indexOf("://") + 3;
    int hostsEnd = indexOfAny(jdbcUrl, hostsStart, '/', '?', '#');
    hostsEnd = hostsEnd < 0 ? jdbcUrl.length() : hostsEnd;
    // A raw @ after the authority may terminate credentials containing an unescaped /, ? or #.
    // Reject it before parsing URL fields, even in query values; encoded %40 is unambiguous.
    if (jdbcUrl.indexOf('@', hostsEnd) >= 0) {
      logger.fine("Unable to determine MySQL/MariaDB host boundaries safely");
      ctx.resolveConfiguredServerTarget(null);
      return;
    }
    HostList parsedHosts =
        MysqlHostParser.parseHostList(jdbcUrl.substring(hostsStart, hostsEnd), mysql);
    List<HostPort> hosts = parsedHosts == null ? null : parsedHosts.hosts();
    boolean hostProperties = parsedHosts != null && parsedHosts.firstHostProperties();
    resolveHostGroup(hosts, ctx);
    String normalizedUrl = jdbcUrl.toLowerCase(Locale.ROOT);
    boolean parsedUrlFields = parseDatabasePath(jdbcUrl, hostsEnd, ctx);
    if (hosts == null || hosts.isEmpty()) {
      if (parsedUrlFields) {
        ctx.applyCommonParams(normalizedUrl, "?", "&");
      }
      return;
    }
    if (parsedUrlFields && hostProperties) {
      // Explicit host properties take precedence over common URL parameters.
      ctx.applyCommonParams(normalizedUrl, "?", "&");
    }
    HostPort firstHost = hosts.get(0);
    ctx.host(firstHost.host());
    if (firstHost.port() != null) {
      ctx.port(firstHost.port());
    }
    if (parsedUrlFields && !hostProperties) {
      ctx.applyCommonParams(normalizedUrl, "?", "&");
    }
    if (hosts.size() == 1) {
      ctx.resolveConfiguredServerTarget(
          DbServerTarget.builder(DEFAULT_PORT)
              .addEndpoint(ctx.host(), ctx.port() == null ? -1 : ctx.port())
              .build());
    }
  }

  private static void resolveHostGroup(@Nullable List<HostPort> hosts, ParseContext ctx) {
    if (hosts == null) {
      ctx.resolveConfiguredServerTarget(null);
      return;
    }
    if (hosts.size() < 2) {
      return;
    }
    DbServerTargetBuilder builder = DbServerTarget.builder(DEFAULT_PORT);
    for (HostPort host : hosts) {
      builder.addEndpoint(host.host(), host.port() == null ? -1 : host.port());
    }
    ctx.resolveConfiguredServerTarget(builder.build());
  }

  private static boolean parseDatabasePath(String jdbcUrl, int hostsEnd, ParseContext ctx) {
    String pathAndQuery = jdbcUrl.substring(hostsEnd).toLowerCase(Locale.ROOT);
    if (extractSubtype(jdbcUrl) != null) {
      int queryStart = pathAndQuery.indexOf('?');
      String path = queryStart < 0 ? pathAndQuery : pathAndQuery.substring(0, queryStart);
      if (path.startsWith("/") && path.length() > 1) {
        ctx.databaseName(path.substring(1));
      }
      return true;
    }
    URI uri;
    try {
      // A relative path keeps a leading // in the database path, not in a URI authority.
      uri = new URI("." + pathAndQuery);
    } catch (URISyntaxException ignored) {
      logger.fine("Unable to parse database path or query");
      return false;
    }
    String path = uri.getPath();
    String prefix = "./";
    if (path != null && path.startsWith(prefix) && path.length() > prefix.length()) {
      ctx.databaseName(path.substring(prefix.length()));
    }
    return true;
  }

  private static void parseNonStandardUrl(String jdbcUrl, ParseContext ctx) {
    int typeEndLoc = jdbcUrl.indexOf(':');
    int sectionEnd = indexOfAny(jdbcUrl, typeEndLoc + 1, ':', '/', '?');
    int portLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == ':') {
      portLoc = sectionEnd;
      sectionEnd = indexOfAny(jdbcUrl, sectionEnd + 1, '/', '?');
    }
    int dbLoc = -1;
    if (sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '/') {
      dbLoc = sectionEnd;
      sectionEnd = indexOfAny(jdbcUrl, sectionEnd + 1, '?');
    }
    int paramLoc = -1;
    if (dbLoc != -1 && sectionEnd != -1 && jdbcUrl.charAt(sectionEnd) == '?') {
      paramLoc = sectionEnd;
    }

    // Extract database name
    if (paramLoc > 0) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1, paramLoc));
    } else if (dbLoc != -1) {
      ctx.databaseName(jdbcUrl.substring(dbLoc + 1));
    }

    // Host and port from URL
    int hostEndLoc;
    // without a database the host/port segment ends at the query string, if any
    int urlEndLoc = dbLoc != -1 ? dbLoc : jdbcUrl.indexOf('?');
    int effectiveDbLoc = urlEndLoc != -1 ? urlEndLoc : jdbcUrl.length();
    if (portLoc > 0) {
      hostEndLoc = portLoc;
      Integer parsedPort = parsePort(jdbcUrl.substring(portLoc + 1, effectiveDbLoc));
      if (parsedPort != null) {
        ctx.port(parsedPort);
      }
    } else {
      hostEndLoc = effectiveDbLoc;
    }
    ctx.host(jdbcUrl.substring(typeEndLoc + 1, hostEndLoc));

    // Apply query params (highest precedence)
    ctx.applyCommonParams(jdbcUrl, "?", "&");
  }
}
