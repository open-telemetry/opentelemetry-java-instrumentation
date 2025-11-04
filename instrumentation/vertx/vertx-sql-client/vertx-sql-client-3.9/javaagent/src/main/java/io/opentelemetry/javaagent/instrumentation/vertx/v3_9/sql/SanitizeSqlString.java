package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class SanitizeSqlString {
  private static final Logger logger = Logger.getLogger(SanitizeSqlString.class.getName());

  private SanitizeSqlString() {}
  // Replace single-quoted string literals: 'foo', with ?
  private static final Pattern SINGLE_QUOTE_STRING =
      Pattern.compile("'([^'\\\\]|\\\\.)*'");

  // Replace double-quoted string literals: "foo", with ?
  // Note: double quotes in MySQL often quote identifiers, but some apps use them for strings.
  private static final Pattern DOUBLE_QUOTE_STRING =
      Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");

  // Collapse IN lists: IN (1, 2, 'a') -> IN (?)
  private static final Pattern IN_CLAUSE =
      Pattern.compile("(?i)\\bIN\\s*\\([^)]*\\)");

  // Numeric literal not adjacent to letters/dot/underscore to avoid replacing column names like col1 or 1.2.3
  // Matches -123, 45.67, 0, .5 (we'll stick with -?\d+(\.\d+)? for safety)
  private static final Pattern NUMERIC_LITERAL =
      Pattern.compile("(?<![A-Za-z0-9_\\.])(-?\\d+(?:\\.\\d+)?)(?![A-Za-z0-9_\\.])");

  // Optional: match SQL hex numbers like 0xABCD (treat as literal)
  private static final Pattern HEX_LITERAL =
      Pattern.compile("(?<![A-Za-z0-9_\\.])0x[0-9A-Fa-f]+(?![A-Za-z0-9_\\.])");

  private static final Pattern DATE_LITERAL =
      Pattern.compile("(?i)(DATE|TIMESTAMP)\\s*'[^']*'");

  public static String sanitize(String sql) {

    if (sql == null || sql.isEmpty()) {
      return sql;
    }
    String s = sql;
    try {

      s = SINGLE_QUOTE_STRING.matcher(s).replaceAll("\\?");
      s = DOUBLE_QUOTE_STRING.matcher(s).replaceAll("\\?");

      // 2) Collapse IN (...) lists to IN (?)
      s = IN_CLAUSE.matcher(s).replaceAll("IN (?)");

      // 3) Replace hex literals (optional)
      s = HEX_LITERAL.matcher(s).replaceAll("?");

      // 4) Replace numeric literals with ?
      s = NUMERIC_LITERAL.matcher(s).replaceAll("?");
      s = DATE_LITERAL.matcher(s).replaceAll("$1 ?");

      // 5) Normalize whitespace: collapse multiple spaces/newlines into single space
      s = s.replaceAll("\\s+", " ").trim();

    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "failed to sanitize SQL string: " + sql, e);
      s = "mysql ??";
    }
    return s;
  }
}
