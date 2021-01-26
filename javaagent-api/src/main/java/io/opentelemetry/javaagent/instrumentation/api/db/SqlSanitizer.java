package io.opentelemetry.javaagent.instrumentation.api.db;

public final class SqlSanitizer {

  public static SqlStatementInfo sanitize(String statement) {
    if (statement == null) {
      return new SqlStatementInfo(null, null, null);
    }
    return AutoSqlSanitizer.sanitize(statement);
  }

  private SqlSanitizer() {}
}
