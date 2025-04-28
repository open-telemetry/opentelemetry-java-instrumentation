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
    return String.format("'%s'", value);
  }

  public static String stringifyParameter(Number value) {
    return String.format("%s", value);
  }

  public static String stringifyParameter(boolean value) {
    return String.format("%s", value);
  }

  @SuppressWarnings("DefaultLocale")
  public static String stringifyParameter(byte value) {
    return String.format("0x%02x", value);
  }

  @SuppressWarnings("DefaultLocale")
  public static String stringifyParameter(byte[] value) {
    StringBuilder builder = new StringBuilder();
    builder.append("0x");
    for (Byte b : value) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }

  public static String stringifyParameter(Date value) {
    return String.format("'%s'", value.toString());
  }

  public static String stringifyParameter(Time value) {
    return String.format("'%s'", value.toString());
  }

  public static String stringifyParameter(Timestamp value) {
    return String.format("'%s'", value.toString());
  }

  public static String stringifyParameter(URL value) {
    return String.format("'%s'", value.toString());
  }

  public static String stringifyParameter(RowId value) {
    return String.format("'%s'", value.toString());
  }

  public static String stringifyNullParameter() {
    return "<null>";
  }

}
