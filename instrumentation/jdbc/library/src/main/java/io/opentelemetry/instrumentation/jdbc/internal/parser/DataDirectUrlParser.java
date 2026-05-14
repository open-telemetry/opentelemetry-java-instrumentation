/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser for DataDirect and TIBCO Software JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>datadirect:sqlserver://host:1433;databaseName=db
 *   <li>datadirect:oracle://host:1521;SID=orcl
 *   <li>datadirect:mysql://host:3306/db
 *   <li>datadirect:postgresql://host:5432/db
 *   <li>datadirect:db2://host:50000/db
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class DataDirectUrlParser implements JdbcUrlParser {

  // copied from DbAttributes.DbSystemNameValues
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MYSQL = "mysql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String POSTGRESQL = "postgresql";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_DB2 = "ibm.db2";

  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String MSSQL = "mssql";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String ORACLE = "oracle";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String DB2 = "db2";

  // DataDirect subtypes mapped to stable db.system.name values
  private static final String SUBTYPE_SQLSERVER = "sqlserver";
  private static final String SUBTYPE_ORACLE = "oracle";
  private static final String SUBTYPE_MYSQL = "mysql";
  private static final String SUBTYPE_POSTGRESQL = "postgresql";
  private static final String SUBTYPE_DB2 = "db2";

  private static final Map<String, String> SUBTYPE_TO_SYSTEM = buildSubtypeToSystem();

  private static Map<String, String> buildSubtypeToSystem() {
    Map<String, String> map = new HashMap<>(5);
    map.put(SUBTYPE_SQLSERVER, MICROSOFT_SQL_SERVER);
    map.put(SUBTYPE_ORACLE, ORACLE_DB);
    map.put(SUBTYPE_MYSQL, MYSQL);
    map.put(SUBTYPE_POSTGRESQL, POSTGRESQL);
    map.put(SUBTYPE_DB2, IBM_DB2);
    return map;
  }

  private static final Map<String, String> SUBTYPE_TO_OLD_SYSTEM = buildSubtypeToOldSystem();

  private static Map<String, String> buildSubtypeToOldSystem() {
    Map<String, String> map = new HashMap<>(3);
    map.put(SUBTYPE_SQLSERVER, MSSQL);
    map.put(SUBTYPE_ORACLE, ORACLE);
    map.put(SUBTYPE_DB2, DB2);
    return map;
  }

  public static final DataDirectUrlParser INSTANCE = new DataDirectUrlParser();

  private DataDirectUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.applyDataSourceProperties();

    int typeEndIndex = jdbcUrl.indexOf(':');
    int subtypeEndIndex = jdbcUrl.indexOf(':', typeEndIndex + 1);

    if (subtypeEndIndex == -1) {
      return;
    }

    String subtype = jdbcUrl.substring(typeEndIndex + 1, subtypeEndIndex);

    // Determine the actual database system based on the subtype
    String system = SUBTYPE_TO_SYSTEM.get(subtype);
    if (system != null) {
      ctx.system(system);
      String oldSystem = SUBTYPE_TO_OLD_SYSTEM.get(subtype);
      if (oldSystem != null) {
        ctx.oldSemconvSystem(oldSystem);
      }
    }

    ctx.subtype(subtype);

    ctx.parseUrl(jdbcUrl);

    // DataDirect/Tibco uses DatabaseName in semicolon-delimited params
    // URL is lowercased by JdbcConnectionUrlParser, so check lowercase param name
    ctx.applyCommonParams(jdbcUrl, ";", ";");
  }
}
