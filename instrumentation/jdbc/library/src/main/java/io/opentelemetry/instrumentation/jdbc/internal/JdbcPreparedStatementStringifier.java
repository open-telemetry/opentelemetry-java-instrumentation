/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import java.net.URL;
import java.sql.Date;
import java.sql.RowId;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JdbcPreparedStatementStringifier {

  private JdbcPreparedStatementStringifier() {}

  public static String stringifyParameter(String value) {
    return value;
  }

  public static String stringifyParameter(Number value) {
    return value != null ? value.toString() : null;
  }

  public static String stringifyParameter(boolean value) {
    return value ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
  }

  public static String stringifyParameter(Date value) {
    return value != null ? value.toString() : null;
  }

  public static String stringifyParameter(Time value) {
    return value != null ? value.toString() : null;
  }

  public static String stringifyParameter(Timestamp value) {
    return value != null ? value.toString() : null;
  }

  public static String stringifyParameter(URL value) {
    return value != null ? value.toString() : null;
  }

  public static String stringifyParameter(RowId value) {
    return value != null ? value.toString() : null;
  }
}
