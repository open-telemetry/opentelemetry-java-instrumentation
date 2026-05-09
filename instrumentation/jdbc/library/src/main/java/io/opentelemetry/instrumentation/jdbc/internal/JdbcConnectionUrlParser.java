/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo.DEFAULT;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.parser.ClickhouseUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.DataDirectUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.Db2UrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.DerbyUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.GenericUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.H2UrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.HsqlUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.InformixDirectUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.InformixSqliUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.JdbcUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.JtdsUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.LindormUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.MssqlUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.MysqlUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.OceanbaseUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.OracleUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.ParseContext;
import io.opentelemetry.instrumentation.jdbc.internal.parser.PolardbUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.PostgresqlUrlParser;
import io.opentelemetry.instrumentation.jdbc.internal.parser.SapUrlParser;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Parses JDBC connection URLs to extract database connection information.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JdbcConnectionUrlParser {

  private static final Logger logger = Logger.getLogger(JdbcConnectionUrlParser.class.getName());

  private static final Map<String, JdbcUrlParser> typeParsers = new HashMap<>();

  static {
    // PostgreSQL
    typeParsers.put("postgresql", PostgresqlUrlParser.INSTANCE);

    // MySQL and MariaDB
    typeParsers.put("mysql", MysqlUrlParser.INSTANCE);
    typeParsers.put("mariadb", MysqlUrlParser.INSTANCE);

    // Microsoft SQL Server
    typeParsers.put("jtds", JtdsUrlParser.INSTANCE);
    typeParsers.put("microsoft", MssqlUrlParser.INSTANCE);
    typeParsers.put("sqlserver", MssqlUrlParser.INSTANCE);

    // Oracle
    typeParsers.put("oracle", OracleUrlParser.INSTANCE);

    // DB2 and AS400
    typeParsers.put("db2", Db2UrlParser.INSTANCE);
    typeParsers.put("as400", Db2UrlParser.INSTANCE);

    // H2
    typeParsers.put("h2", H2UrlParser.INSTANCE);

    // HyperSQL (HSQLDB)
    typeParsers.put("hsqldb", HsqlUrlParser.INSTANCE);

    // Apache Derby
    typeParsers.put("derby", DerbyUrlParser.INSTANCE);

    // SAP HANA
    typeParsers.put("sap", SapUrlParser.INSTANCE);

    // DataDirect and TIBCO
    typeParsers.put("datadirect", DataDirectUrlParser.INSTANCE);
    typeParsers.put("tibcosoftware", DataDirectUrlParser.INSTANCE);

    // Informix
    typeParsers.put("informix-sqli", InformixSqliUrlParser.INSTANCE);
    typeParsers.put("informix-direct", InformixDirectUrlParser.INSTANCE);

    // ClickHouse
    typeParsers.put("clickhouse", ClickhouseUrlParser.INSTANCE);

    // OceanBase
    typeParsers.put("oceanbase", OceanbaseUrlParser.INSTANCE);

    // Lindorm
    typeParsers.put("lindorm", LindormUrlParser.INSTANCE);

    // PolarDB
    typeParsers.put("polardb", PolardbUrlParser.INSTANCE);
  }

  private JdbcConnectionUrlParser() {}

  /**
   * Parse a JDBC connection URL and extract database connection information.
   *
   * @param connectionUrl the JDBC connection URL
   * @param props optional connection properties
   * @return the parsed DbInfo, or DbInfo.DEFAULT for null/invalid non-JDBC inputs; parser failures
   *     return the best-effort result accumulated before the failure
   */
  public static DbInfo parse(String connectionUrl, Properties props) {
    if (connectionUrl == null) {
      return DEFAULT;
    }

    // Make this easier and ignore case.
    connectionUrl = connectionUrl.toLowerCase(Locale.ROOT);

    String jdbcUrl = stripJdbcPrefix(connectionUrl);
    if (jdbcUrl == null) {
      return DEFAULT;
    }

    int typeLoc = jdbcUrl.indexOf(':');
    if (typeLoc < 1) {
      // Invalid format: `jdbc:` or `jdbc::`
      return DEFAULT;
    }

    String type = jdbcUrl.substring(0, typeLoc);
    JdbcUrlParser parser = typeParsers.get(type);
    ParseContext ctx = ParseContext.of(type, props);

    try {
      if (parser == null) {
        ctx.applyDataSourceProperties();
        GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);
      } else {
        parser.parse(jdbcUrl, ctx);
      }

      return ctx.toDbInfo();
    } catch (RuntimeException e) {
      logger.log(FINE, "Error parsing URL", e);
      return ctx.toDbInfo();
    }
  }

  private static String stripJdbcPrefix(String connectionUrl) {
    if (connectionUrl.startsWith("jdbc:tracing:")) {
      // see https://github.com/opentracing-contrib/java-jdbc
      return connectionUrl.substring("jdbc:tracing:".length());
    } else if (connectionUrl.startsWith("jdbc:")) {
      return connectionUrl.substring("jdbc:".length());
    } else if (connectionUrl.startsWith("jdbc-secretsmanager:tracing:")) {
      return connectionUrl.substring("jdbc-secretsmanager:tracing:".length());
    } else if (connectionUrl.startsWith("jdbc-secretsmanager:")) {
      return connectionUrl.substring("jdbc-secretsmanager:".length());
    }
    return null;
  }
}
