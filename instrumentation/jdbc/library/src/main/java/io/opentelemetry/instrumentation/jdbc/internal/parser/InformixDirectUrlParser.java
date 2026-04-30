/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.indexOfAny;

/**
 * Parser for IBM Informix Direct JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>informix-direct://dbname
 *   <li>informix-direct://dbname:INFORMIXSERVER=server
 *   <li>informix-direct://dbname;user=informix;password=pass
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class InformixDirectUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String IBM_INFORMIX = "ibm.informix";
  private static final String OLD_SYSTEM = "informix-direct";

  public static final InformixDirectUrlParser INSTANCE = new InformixDirectUrlParser();

  private InformixDirectUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(IBM_INFORMIX);
    ctx.oldSemconvSystem(OLD_SYSTEM);

    ctx.applyUserProperty();

    // Extract user/db from semicolon-delimited URL params
    ctx.applyCommonParams(jdbcUrl, ";", ";");

    // For direct connections, extract just the database name (no host/port)
    // URL format: informix-direct://dbname:... or informix-direct://dbname;...
    int protoIndex = jdbcUrl.indexOf("://");
    if (protoIndex >= 0) {
      String remainder = jdbcUrl.substring(protoIndex + 3);
      int endIndex = indexOfAny(remainder, ':', ';');
      String databaseName = endIndex >= 0 ? remainder.substring(0, endIndex) : remainder;
      if (!databaseName.isEmpty()) {
        ctx.databaseName(databaseName);
      }
    }
  }
}
