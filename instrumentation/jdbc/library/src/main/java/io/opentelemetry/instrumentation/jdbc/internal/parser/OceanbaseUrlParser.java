/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.extractSubtype;

/**
 * Parser for OceanBase JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>jdbc:oceanbase://host:port/dbname
 *   <li>jdbc:oceanbase:oracle://host:port/dbname
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class OceanbaseUrlParser implements JdbcUrlParser {

  private static final String SYSTEM = "oceanbase";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String ORACLE = "oracle";

  public static final OceanbaseUrlParser INSTANCE = new OceanbaseUrlParser();

  private OceanbaseUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(SYSTEM);

    ctx.applyUserProperty();

    String subtype = extractSubtype(jdbcUrl);

    if (subtype != null) {
      // Has subtype (e.g., oceanbase:oracle://...)
      if (subtype.equals(ORACLE)) {
        // Override system for Oracle mode
        ctx.system(ORACLE_DB);
        ctx.oldSemconvSystem(ORACLE);
      }
      ctx.subtype(subtype);
      ctx.parseUrl(jdbcUrl);
    } else {
      GenericUrlParser.INSTANCE.parse(jdbcUrl, ctx);
    }
  }
}
