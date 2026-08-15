/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqlDialectUtil {

  public static SqlDialect fromDbSystemName(@Nullable String dbSystemName) {
    if (dbSystemName == null) {
      return DOUBLE_QUOTES_ARE_STRING_LITERALS;
    }
    switch (dbSystemName) {
      case "postgresql":
      case "oracle.db":
      case "ibm.db2":
      case "derby":
      case "hsqldb":
      case "sap.hana":
      case "clickhouse":
      case "polardb":
        return DOUBLE_QUOTES_ARE_IDENTIFIERS;
      default:
        // Favor sanitization when the database may interpret double-quoted tokens as literals.
        return DOUBLE_QUOTES_ARE_STRING_LITERALS;
    }
  }

  private SqlDialectUtil() {}
}
