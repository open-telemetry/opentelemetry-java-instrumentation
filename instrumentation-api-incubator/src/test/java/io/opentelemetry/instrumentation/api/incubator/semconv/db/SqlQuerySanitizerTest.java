/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SqlQuerySanitizerTest {

  private static final SqlQuerySanitizer SANITIZER = SqlQuerySanitizer.create(true);

  private static SqlQuery sanitize(String sql) {
    return emitStableDatabaseSemconv()
        ? SANITIZER.sanitizeWithSummary(sql, DOUBLE_QUOTES_ARE_STRING_LITERALS)
        : SANITIZER.sanitize(sql, DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }

  private static SqlQuery sanitize(String sql, SqlDialect dialect) {
    return emitStableDatabaseSemconv()
        ? SANITIZER.sanitizeWithSummary(sql, dialect)
        : SANITIZER.sanitize(sql, dialect);
  }

  @ParameterizedTest
  @MethodSource("sqlArgs")
  void sanitizeSql(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = sanitize(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo(expected);
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
    } else {
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("couchbaseArgs")
  void normalizeCouchbase(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = sanitize(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo(expected);
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
    } else {
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("simplifyArgs")
  void simplifySql(String original, Function<String, SqlQuery> expectedFunction) {
    SqlQuery result = sanitize(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    } else {
      assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
      assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
      assertThat(result.getQuerySummary()).isNull();
    }
    assertThat(result.getStoredProcedureName()).isEqualTo(expected.getStoredProcedureName());
  }

  @ParameterizedTest
  @MethodSource("simplifyDefaultArgs")
  void simplifySqlDefault(String original, Function<String, SqlQuery> expectedFunction) {
    SqlQuery result = sanitize(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    } else {
      assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
      assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
      assertThat(result.getQuerySummary()).isNull();
    }
    assertThat(result.getStoredProcedureName()).isEqualTo(expected.getStoredProcedureName());
  }

  @ParameterizedTest
  @MethodSource("sensitiveArgs")
  void sanitizeSensitive(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = sanitize(original);
    assertThat(result.getQueryText()).isEqualTo(expected);
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
    } else {
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  private static Stream<Arguments> sensitiveArgs() {
    return Stream.of(
        // SAP HANA CONNECT and CREATE USER statements can contain unquoted password
        // https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20d3b9ad751910148cdccc8205563a87.html?locale=en-US
        Arguments.of("CONNECT user PASSWORD Password1", "CONNECT ?", null),
        Arguments.of("CREATE USER new_user PASSWORD Password1", "CREATE USER ?", "CREATE"),
        Arguments.of("ALTER USER user PASSWORD Password1", "ALTER USER ?", "ALTER"),
        // Oracle CREATE USER statement can contain unquoted password
        // https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_8003.htm
        Arguments.of("CREATE USER new_user IDENTIFIED BY Password1", "CREATE USER ?", "CREATE"),
        Arguments.of(
            "ALTER USER user IDENTIFIED BY Password1 REPLACE Password2", "ALTER USER ?", "ALTER"),
        // field named "connect" does not trigger sanitization
        Arguments.of("SELECT connect FROM TABLE", "SELECT connect FROM TABLE", "SELECT TABLE"));
  }

  @Test
  void veryLongSelectStatementsAreOk() {
    StringBuilder sb = new StringBuilder("SELECT * FROM table WHERE");
    for (int i = 0; i < 2000; i++) {
      sb.append(" column").append(i).append("=123 and");
    }
    String query = sb.toString();

    String sanitizedQuery = query.replace("=123", "=?").substring(0, AutoSqlSanitizer.LIMIT);

    SqlQuery result = sanitize(query);

    assertThat(result.getQueryText()).isEqualTo(sanitizedQuery);
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo("SELECT table");
    } else {
      assertThat(result.getOperationName()).isEqualTo("SELECT");
      assertThat(result.getCollectionName()).isEqualTo("table");
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("ddlArgs")
  void checkDdlOperationStatementsAreOk(String actual, Function<String, SqlQuery> expectFunc) {
    SqlQuery result = sanitize(actual, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery expected = expectFunc.apply(actual);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    } else {
      assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
      assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
      assertThat(result.getQuerySummary()).isNull();
    }
    assertThat(result.getStoredProcedureName()).isEqualTo(expected.getStoredProcedureName());
  }

  @Test
  void lotsOfTicksDontCauseStackOverflowOrLongRuntimes() {
    String s = "'";
    SqlQuerySanitizer sanitizer = SqlQuerySanitizer.create(true);
    for (int i = 0; i < 10000; i++) {
      assertThat(sanitizer.sanitize(s, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isNotNull();
      s += "'";
    }
  }

  @Test
  void veryLongNumbersAreOk() {
    String s = "";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlQuery result = SqlQuerySanitizer.create(true).sanitize(s, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo("?");
  }

  @Test
  void veryLongNumbersAtEndOfTableAreOk() {
    String s = "A";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlQuery result = SqlQuerySanitizer.create(true).sanitize(s, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo(s.substring(0, AutoSqlSanitizer.LIMIT));
  }

  @Test
  void test32kTruncation() {
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    SqlQuery result = sanitize(s.toString());
    assertThat(result.getQueryText().length()).isLessThanOrEqualTo(AutoSqlSanitizer.LIMIT);
    assertThat(result.getQueryText()).doesNotContain("1234");
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getQuerySummary()).startsWith("SELECT TABLE SELECT TABLE SELECT TABLE");
    } else {
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  @Test
  void randomBytesDontCauseExceptionsOrTimeouts() {
    Random r = new Random(0);
    for (int i = 0; i < 1000; i++) {
      StringBuffer sb = new StringBuffer();
      for (int c = 0; c < 1000; c++) {
        sb.append((char) r.nextInt(Character.MAX_VALUE));
      }
      SqlQuerySanitizer.create(true).sanitize(sb.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS);
    }
  }

  @Test
  void longInStatementDoesntCauseStackOverflow() {
    StringBuilder s = new StringBuilder("select col from table where col in (");
    for (int i = 0; i < 10000; i++) {
      s.append("?,");
    }
    s.append("?)");

    String sanitized =
        SqlQuerySanitizer.create(true)
            .sanitize(s.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();

    assertThat(sanitized).isEqualTo("select col from table where col in (?)");
  }

  @Test
  void largeQueryCached() {
    // test that short query is cached
    String shortQuery = "SELECT * FROM TABLE WHERE FIELD = 1234";
    String sanitizedShort =
        SqlQuerySanitizer.create(true)
            .sanitize(shortQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();
    assertThat(sanitizedShort).doesNotContain("1234");
    assertThat(SqlQuerySanitizer.isCached(shortQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isTrue();

    // test that large query is not cached
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    String largeQuery = s.toString();
    String sanitizedLarge =
        SqlQuerySanitizer.create(true)
            .sanitize(largeQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();
    assertThat(sanitizedLarge).doesNotContain("1234");
    assertThat(SqlQuerySanitizer.isCached(largeQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isFalse();
  }

  @Test
  void querySummaryIsTruncated() {
    assumeTrue(emitStableDatabaseSemconv());
    // Build a query with many tables to exceed 255 character limit
    StringBuilder sql = new StringBuilder("SELECT * FROM ");
    for (int i = 0; i < 50; i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("very_long_table_name_").append(i);
    }
    String result =
        SqlQuerySanitizer.create(true)
            .sanitizeWithSummary(sql.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQuerySummary();
    assertThat(result).isNotNull();
    assertThat(result)
        .isEqualTo(
            "SELECT very_long_table_name_0 very_long_table_name_1 very_long_table_name_2 very_long_table_name_3 very_long_table_name_4 very_long_table_name_5 very_long_table_name_6 very_long_table_name_7 very_long_table_name_8 very_long_table_name_9");
    assertThat(result.length()).isEqualTo(236);
  }

  private static Stream<Arguments> sqlArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD=1234",
            "SELECT * FROM TABLE WHERE FIELD=?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = 1234",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD>=-1234",
            "SELECT * FROM TABLE WHERE FIELD>=?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD<-1234",
            "SELECT * FROM TABLE WHERE FIELD<?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD <.1234",
            "SELECT * FROM TABLE WHERE FIELD <?",
            "SELECT TABLE"),
        Arguments.of("SELECT 1.2", "SELECT ?", "SELECT"),
        Arguments.of("SELECT -1.2", "SELECT ?", "SELECT"),
        Arguments.of("SELECT -1.2e-9", "SELECT ?", "SELECT"),
        Arguments.of("SELECT 2E+9", "SELECT ?", "SELECT"),
        Arguments.of("SELECT +0.2", "SELECT ?", "SELECT"),
        Arguments.of("SELECT .2", "SELECT ?", "SELECT"),
        Arguments.of("7", "?", null),
        Arguments.of(".7", "?", null),
        Arguments.of("-7", "?", null),
        Arguments.of("+7", "?", null),
        Arguments.of("SELECT 0x0af764", "SELECT ?", "SELECT"),
        Arguments.of("SELECT 0xdeadBEEF", "SELECT ?", "SELECT"),
        Arguments.of("SELECT * FROM \"TABLE\"", "SELECT * FROM ?", "SELECT \"TABLE\""),

        // Not numbers but could be confused as such
        Arguments.of("SELECT A + B", "SELECT A + B", "SELECT"),
        Arguments.of("SELECT -- comment", "SELECT -- comment", "SELECT"),
        Arguments.of("SELECT * FROM TABLE123", "SELECT * FROM TABLE123", "SELECT TABLE123"),
        Arguments.of(
            "SELECT FIELD2 FROM TABLE_123 WHERE X<>7",
            "SELECT FIELD2 FROM TABLE_123 WHERE X<>?",
            "SELECT TABLE_123"),

        // Semi-nonsensical almost-numbers to elide or not
        Arguments.of("SELECT DEADBEEF", "SELECT DEADBEEF", "SELECT"),
        Arguments.of("SELECT 123-45-6789", "SELECT ?", "SELECT"),
        Arguments.of("SELECT 1/2/34", "SELECT ?/?/?", "SELECT"),

        // Basic ' strings
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = ''",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = 'words and spaces'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = ' an escaped '' quote mark inside'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = '\\\\'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = '\"inside doubles\"'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = '\"$$$$\"'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = 'a single \" doublequote inside'",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),

        // Some databases allow using dollar-quoted strings
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $$$$",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $$words and spaces$$",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $$quotes '\" inside$$",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $$\"''\"$$",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $$\\\\$$",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),

        // PostgreSQL native parameter marker, we want to keep $1 instead of replacing it with ?
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $1",
            "SELECT * FROM TABLE WHERE FIELD = $1",
            "SELECT TABLE"),

        // Unicode, including a unicode identifier with a trailing number
        Arguments.of(
            "SELECT * FROM TABLEओ7 WHERE FIELD = 'ɣ'",
            "SELECT * FROM TABLEओ7 WHERE FIELD = ?",
            "SELECT TABLEओ7"),

        // whitespace normalization
        Arguments.of(
            "SELECT    *    \t\r\nFROM  TABLE WHERE FIELD1 = 12344 AND FIELD2 = 5678",
            "SELECT * FROM TABLE WHERE FIELD1 = ? AND FIELD2 = ?",
            "SELECT TABLE"),

        // hibernate/jpa query language
        Arguments.of("FROM TABLE WHERE FIELD=1234", "FROM TABLE WHERE FIELD=?", "SELECT TABLE"));
  }

  private static Stream<Arguments> couchbaseArgs() {
    return Stream.of(
        // Some databases support/encourage " instead of ' with same escape rules
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"words and spaces'\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \" an escaped \"\" quote mark inside\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"\\\\\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"'inside singles'\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"'$$$$'\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = \"a single ' singlequote inside\"",
            "SELECT * FROM TABLE WHERE FIELD = ?",
            "SELECT TABLE"));
  }

  private static Function<String, SqlQuery> expect(
      String operation, String collectionName, String querySummary) {
    return sql ->
        emitStableDatabaseSemconv()
            ? SqlQuery.createWithSummary(sql, null, querySummary)
            : SqlQuery.create(sql, operation, collectionName);
  }

  private static Function<String, SqlQuery> expect(
      String sql, String operation, String collectionName, String querySummary) {
    return ignored ->
        emitStableDatabaseSemconv()
            ? SqlQuery.createWithSummary(sql, null, querySummary)
            : SqlQuery.create(sql, operation, collectionName);
  }

  private static Function<String, SqlQuery> expectStoredProcedure(
      String operation, String storedProcedureName, String querySummary) {
    return sql ->
        emitStableDatabaseSemconv()
            ? SqlQuery.createWithSummary(sql, storedProcedureName, querySummary)
            : SqlQuery.create(sql, operation, storedProcedureName);
  }

  private static Function<String, SqlQuery> expectStoredProcedure(
      String sql, String operation, String storedProcedureName, String querySummary) {
    return ignored ->
        emitStableDatabaseSemconv()
            ? SqlQuery.createWithSummary(sql, storedProcedureName, querySummary)
            : SqlQuery.create(sql, operation, storedProcedureName);
  }

  private static Stream<Arguments> simplifyArgs() {
    return Stream.of(
        // Select
        Arguments.of(
            "SELECT x, y, z FROM schema.table",
            expect("SELECT", "schema.table", "SELECT schema.table")),
        Arguments.of(
            "SELECT x, y, z FROM `schema table`",
            expect("SELECT", "schema table", "SELECT `schema table`")),
        Arguments.of(
            "SELECT x, y, z FROM `schema`.`table`",
            expect("SELECT", "`schema`.`table`", "SELECT `schema`.`table`")),
        Arguments.of(
            "SELECT x, y, z FROM \"schema table\"",
            expect("SELECT x, y, z FROM ?", "SELECT", "schema table", "SELECT \"schema table\"")),
        // double-quoted dot-separated identifiers are matched as IDENTIFIER, not DOUBLE_QUOTED_STR,
        // so they are always preserved regardless of dialect
        Arguments.of(
            "SELECT x, y, z FROM \"schema\".\"table\"",
            expect("SELECT", "\"schema\".\"table\"", "SELECT \"schema\".\"table\"")),
        Arguments.of(
            "WITH subquery as (select a from b) SELECT x, y, z FROM table",
            expect("SELECT", null, "SELECT b SELECT table")),
        Arguments.of(
            "SELECT x, y, (select a from b) as z FROM table",
            expect("SELECT", null, "SELECT SELECT b table")),
        Arguments.of(
            "select delete, insert into, merge, update from table", // invalid SQL
            expect("SELECT", "table", "SELECT table")),
        Arguments.of(
            "select col /* from table2 */ from table", expect("SELECT", "table", "SELECT table")),
        Arguments.of(
            "select col from table join anotherTable",
            expect("SELECT", null, "SELECT table anotherTable")),
        Arguments.of(
            "SELECT * FROM t1 LEFT JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.x = t3.x",
            expect("SELECT", null, "SELECT t1 t2 t3")),
        Arguments.of(
            "select col from (select * from anotherTable)",
            expect("SELECT", null, "SELECT SELECT anotherTable")),
        Arguments.of(
            "select col from (select * from anotherTable) alias",
            expect("SELECT", null, "SELECT SELECT anotherTable")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) sub, t3",
            expect("SELECT", null, "SELECT SELECT t1 t3")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) s1, (SELECT * FROM t2) s2",
            expect("SELECT", null, "SELECT SELECT t1 SELECT t2")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) sub, t2 JOIN t3 ON t2.id = t3.id",
            expect("SELECT", null, "SELECT SELECT t1 t2 t3")),
        Arguments.of(
            "select col from table1 union select col from table2",
            expect("SELECT", null, "SELECT table1 SELECT table2")),
        Arguments.of(
            "SELECT id, name FROM employees UNION ALL SELECT id, name FROM contractors UNION SELECT id, name FROM vendors",
            expect("SELECT", null, "SELECT employees SELECT contractors SELECT vendors")),
        // Parenthesized table with UNION - identifier cancels pending subquery push
        Arguments.of(
            "SELECT * FROM (t UNION SELECT * FROM t2), t3",
            expect("SELECT", null, "SELECT t SELECT t2")),
        Arguments.of(
            "select id, (select max(foo) from (select foo from foos union all select foo from bars)) as foo from main_table",
            expect("SELECT", null, "SELECT SELECT SELECT foos SELECT bars main_table")),
        Arguments.of(
            "select col from table where col in (select * from anotherTable)",
            expect("SELECT", null, "SELECT table SELECT anotherTable")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM inner1 JOIN inner2 ON inner1.id = inner2.id) AS sub",
            expect("SELECT", null, "SELECT SELECT inner1 inner2")),
        Arguments.of(
            "SELECT * FROM (VALUES (1,2), (3,4)) AS t(a, b)",
            expect("SELECT * FROM (VALUES (?,?), (?,?)) AS t(a, b)", "SELECT", null, "SELECT")),
        Arguments.of("SELECT * FROM t1 CROSS APPLY t2", expect("SELECT", "t1", "SELECT t1 t2")),
        Arguments.of(
            "SELECT * FROM t1 OUTER APPLY (SELECT * FROM t2 WHERE t2.id = t1.id)",
            expect("SELECT", null, "SELECT t1 SELECT t2")),
        Arguments.of(
            "SELECT * FROM t1, LATERAL (SELECT * FROM t2 WHERE t2.id = t1.id)",
            expect("SELECT", null, "SELECT t1 SELECT t2")),
        Arguments.of(
            "select col from table1, table2", expect("SELECT", null, "SELECT table1 table2")),
        Arguments.of(
            "select col from table1 t1, table2 t2", expect("SELECT", null, "SELECT table1 table2")),
        Arguments.of(
            "select col from table1 as t1, table2 as t2",
            expect("SELECT", null, "SELECT table1 table2")),
        Arguments.of(
            "select col from table where col in (1, 2, 3)",
            expect("select col from table where col in (?)", "SELECT", "table", "SELECT table")),
        Arguments.of(
            "select 'a' IN(x, 'b') from table where col in (1) and z IN( '3', '4' )",
            expect(
                "select ? IN(x, ?) from table where col in (?) and z IN(?)",
                "SELECT",
                "table",
                "SELECT table")),
        Arguments.of(
            "select col from table order by col, col2", expect("SELECT", "table", "SELECT table")),
        Arguments.of(
            "select ąś∂ń© from źćļńĶ order by col, col2",
            expect("SELECT", "źćļńĶ", "SELECT źćļńĶ")),
        Arguments.of("select 12345678", expect("select ?", "SELECT", null, "SELECT")),
        Arguments.of(
            "/* update comment */ select * from table1",
            expect("SELECT", "table1", "SELECT table1")),
        Arguments.of("select /*((*/abc from table", expect("SELECT", "table", "SELECT table")),
        Arguments.of("SeLeCT * FrOm TAblE", expect("SELECT", "TAblE", "SELECT TAblE")),
        Arguments.of("select next value in hibernate_sequence", expect("SELECT", null, "SELECT")),

        // EXPLAIN - preserved as prefix command in summary
        Arguments.of(
            "EXPLAIN SELECT * FROM users", expect("SELECT", "users", "EXPLAIN SELECT users")),

        // hibernate/jpa
        Arguments.of("FROM schema.table", expect("SELECT", "schema.table", "SELECT schema.table")),
        Arguments.of(
            "/* update comment */ from table1", expect("SELECT", "table1", "SELECT table1")),

        // Insert
        Arguments.of(" insert into table where lalala", expect("INSERT", "table", "INSERT table")),
        Arguments.of(
            "insert insert into table where lalala", // invalid SQL
            expect("INSERT", "table", "INSERT table")),
        Arguments.of(
            "insert into db.table where lalala", expect("INSERT", "db.table", "INSERT db.table")),
        Arguments.of(
            "insert into `db table` where lalala",
            expect("INSERT", "db table", "INSERT `db table`")),
        Arguments.of(
            "insert into \"db table\" where lalala",
            expect("insert into ? where lalala", "INSERT", "db table", "INSERT \"db table\"")),
        Arguments.of("insert without i-n-t-o", expect("INSERT", null, "INSERT")),

        // Delete
        Arguments.of(
            "delete from table where something something",
            expect("DELETE", "table", "DELETE table")),
        Arguments.of(
            "delete from `my table` where something something",
            expect("DELETE", "my table", "DELETE `my table`")),
        Arguments.of(
            "delete from \"my table\" where something something",
            expect(
                "delete from ? where something something",
                "DELETE",
                "my table",
                "DELETE \"my table\"")),
        Arguments.of(
            "delete from foo where x IN (1,2,3)",
            expect("delete from foo where x IN (?)", "DELETE", "foo", "DELETE foo")),
        Arguments.of("delete from 12345678", expect("delete from ?", "DELETE", null, "DELETE")),
        Arguments.of("delete   (((", expect("delete (((", "DELETE", null, "DELETE")),

        // Update
        Arguments.of(
            "update table set answer=42",
            expect("update table set answer=?", "UPDATE", "table", "UPDATE table")),
        Arguments.of(
            "update `my table` set answer=42",
            expect("update `my table` set answer=?", "UPDATE", "my table", "UPDATE `my table`")),
        Arguments.of(
            "update `my table` set answer=42 where x IN('a', 'b') AND y In ('a',  'b')",
            expect(
                "update `my table` set answer=? where x IN(?) AND y In (?)",
                "UPDATE",
                "my table",
                "UPDATE `my table`")),
        Arguments.of(
            "update \"my table\" set answer=42",
            expect("update ? set answer=?", "UPDATE", "my table", "UPDATE \"my table\"")),
        Arguments.of("update /*table", expect("UPDATE", null, "UPDATE")),

        // Call
        Arguments.of(
            "call test_proc()", expectStoredProcedure("CALL", "test_proc", "CALL test_proc")),
        Arguments.of(
            "call test_proc", expectStoredProcedure("CALL", "test_proc", "CALL test_proc")),
        // Hibernate uses "call next value for sequence" on HSQLDB and H2 to get sequence values,
        // while it uses SELECT for this on most other databases.
        Arguments.of(
            "call next value for hibernate_sequence",
            expect("CALL", null, "CALL hibernate_sequence")),
        Arguments.of(
            "call db.test_proc",
            expectStoredProcedure("CALL", "db.test_proc", "CALL db.test_proc")),

        // Merge
        Arguments.of("merge into table", expect("MERGE", "table", "MERGE table")),
        Arguments.of("merge into `my table`", expect("MERGE", "my table", "MERGE `my table`")),
        Arguments.of(
            "merge into \"my table\"",
            expect("merge into ?", "MERGE", "my table", "MERGE \"my table\"")),
        Arguments.of(
            "merge table (into is optional in some dbs)", expect("MERGE", "table", "MERGE table")),
        Arguments.of("merge (into )))", expect("MERGE", null, "MERGE")),

        // Unknown operation
        Arguments.of("and now for something completely different", expect(null, null, null)),
        Arguments.of("", expect(null, null, null)),
        Arguments.of(null, expect(null, null, null)),

        // Embedded SELECT in DML operations
        Arguments.of(
            "INSERT INTO t1 SELECT * FROM t2", expect("INSERT", "t1", "INSERT t1 SELECT t2")),
        Arguments.of(
            "DELETE FROM t1 WHERE x IN (SELECT y FROM t2)",
            expect("DELETE", "t1", "DELETE t1 SELECT t2")),
        Arguments.of(
            "UPDATE t1 SET x = (SELECT y FROM t2)", expect("UPDATE", "t1", "UPDATE t1 SELECT t2")),

        // Multi-statement SQL with semicolons
        Arguments.of(
            "SELECT * FROM t1; SELECT * FROM t2",
            expect("SELECT * FROM t1; SELECT * FROM t2", "SELECT", null, "SELECT t1; SELECT t2")),
        Arguments.of(
            "SELECT * FROM t1; INSERT INTO t2 VALUES (1)",
            expect(
                "SELECT * FROM t1; INSERT INTO t2 VALUES (?)",
                "SELECT",
                "t1",
                "SELECT t1; INSERT t2")),

        // Trailing semicolons
        Arguments.of("SELECT * FROM t;", expect("SELECT * FROM t;", "SELECT", "t", "SELECT t")),
        Arguments.of("SELECT * FROM t;;", expect("SELECT * FROM t;;", "SELECT", "t", "SELECT t")),

        // PostgreSQL ONLY keyword (inherits from parent table only, not children)
        Arguments.of(
            "SELECT * FROM ONLY parent",
            expect("SELECT * FROM ONLY parent", "SELECT", "ONLY", "SELECT parent")),
        Arguments.of(
            "DELETE FROM ONLY parent WHERE id = 1",
            expect("DELETE FROM ONLY parent WHERE id = ?", "DELETE", "ONLY", "DELETE parent")),
        Arguments.of(
            "UPDATE ONLY parent SET x = 1",
            expect("UPDATE ONLY parent SET x = ?", "UPDATE", "ONLY", "UPDATE parent")),

        // Standalone VALUES clause
        Arguments.of("VALUES (1)", expect("VALUES (?)", null, null, "VALUES")),
        Arguments.of(
            "VALUES (1, 'a'), (2, 'b')", expect("VALUES (?, ?), (?, ?)", null, null, "VALUES")),

        // EXECUTE/EXEC stored procedure calls
        Arguments.of(
            "EXEC my_procedure",
            emitStableDatabaseSemconv()
                ? expectStoredProcedure("EXEC", "my_procedure", "EXEC my_procedure")
                : expect("EXEC my_procedure", null, null, null)),
        Arguments.of(
            "EXECUTE db.my_procedure @param=1",
            emitStableDatabaseSemconv()
                ? expectStoredProcedure(
                    "EXECUTE db.my_procedure @param=?",
                    "EXECUTE",
                    "db.my_procedure",
                    "EXECUTE db.my_procedure")
                : expect("EXECUTE db.my_procedure @param=?", null, null, null)),

        // SQL Server bracket-quoted identifiers
        Arguments.of("SELECT * FROM [my table]", expect("SELECT", "my", "SELECT [my table]")),
        Arguments.of(
            "SELECT * FROM [schema].[table]",
            expect("SELECT", "schema", "SELECT [schema].[table]")),
        Arguments.of(
            "SELECT [column] FROM [table] WHERE [field] = 1",
            expect(
                "SELECT [column] FROM [table] WHERE [field] = ?",
                "SELECT",
                "table",
                "SELECT [table]")),

        // MySQL escaped backticks
        Arguments.of(
            "SELECT * FROM `my``table`",
            // collection name should be "my`table"
            // not fixing as collection name is only captured by old semconv jflex
            expect("SELECT", "my", "SELECT `my``table`")),
        Arguments.of(
            "SELECT * FROM `table` WHERE `col``name` = 1",
            expect(
                "SELECT * FROM `table` WHERE `col``name` = ?",
                "SELECT",
                "table",
                "SELECT `table`")),

        // Empty and trivial statements should have null query summary
        Arguments.of(";", expect(";", null, null, null)),
        Arguments.of(";;", expect(";;", null, null, null)),
        Arguments.of("   ;   ", expect(" ; ", null, null, null)),
        Arguments.of("/* comment only */", expect("/* comment only */", null, null, null)),
        Arguments.of("   ", expect(" ", null, null, null)),
        Arguments.of("", expect("", null, null, null)),

        // Line comments
        Arguments.of(
            "-- This query will DELETE old records\nSELECT * FROM users WHERE active = 1",
            expect(
                "-- This query will DELETE old records SELECT * FROM users WHERE active = ?",
                "SELECT",
                "users",
                "SELECT users")),
        // Line comment without space after -- (invalid on MySQL/MariaDB)
        Arguments.of(
            "--This query will DELETE old records\nSELECT * FROM users WHERE active = 1",
            expect(
                "--This query will DELETE old records SELECT * FROM users WHERE active = ?",
                "SELECT",
                "users",
                "SELECT users")),
        // "--83" is a line comment per SQL standard and on most databases
        // (except MySQL/MariaDB which requires a space after "--"
        // in which case )
        Arguments.of("SELECT --83", expect("SELECT --83", "SELECT", null, "SELECT")),

        // PostgreSQL tagged dollar-quoted strings: $tag$string$tag$
        // https://neon.com/postgresql/postgresql-plpgsql/dollar-quoted-string-constants
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $tag$hello world$tag$",
            expect("SELECT * FROM TABLE WHERE FIELD = ?", "SELECT", "TABLE", "SELECT TABLE")),
        Arguments.of(
            "SELECT * FROM TABLE WHERE FIELD = $a$nested $b$value$b$ here$a$",
            expect("SELECT * FROM TABLE WHERE FIELD = ?", "SELECT", "TABLE", "SELECT TABLE")),

        // Subqueries in comma-separated FROM lists - state properly isolated via operation stack
        Arguments.of(
            "SELECT * FROM a, (SELECT * FROM b), c", expect("SELECT", null, "SELECT a SELECT b c")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM inner1), (SELECT * FROM inner2), outer_table",
            expect("SELECT", null, "SELECT SELECT inner1 SELECT inner2 outer_table")),

        // Parenthesized table name - not a subquery - valid on MySQL
        Arguments.of("SELECT * FROM (TABLE)", expect("SELECT", null, "SELECT TABLE")),

        // TRUNCATE statement
        Arguments.of(
            "TRUNCATE TABLE users",
            expect("TRUNCATE TABLE users", null, null, "TRUNCATE TABLE users")),
        Arguments.of("TRUNCATE users", expect("TRUNCATE users", null, null, "TRUNCATE users")),
        Arguments.of(
            "TRUNCATE TABLE schema.table",
            expect("TRUNCATE TABLE schema.table", null, null, "TRUNCATE TABLE schema.table")),

        // REPLACE statement (MySQL)
        Arguments.of(
            "REPLACE INTO users VALUES (1, 'name')",
            expect("REPLACE INTO users VALUES (?, ?)", null, null, "REPLACE users")),
        Arguments.of(
            "REPLACE users SET name = 'foo'",
            expect("REPLACE users SET name = ?", null, null, "REPLACE users")),
        Arguments.of(
            "REPLACE INTO db.table (col) VALUES (1)",
            expect("REPLACE INTO db.table (col) VALUES (?)", null, null, "REPLACE db.table")),

        // Transaction control statements
        Arguments.of("BEGIN", expect("BEGIN", null, null, "BEGIN")),
        Arguments.of(
            "BEGIN TRANSACTION", expect("BEGIN TRANSACTION", null, null, "BEGIN TRANSACTION")),
        Arguments.of("COMMIT", expect("COMMIT", null, null, "COMMIT")),
        Arguments.of(
            "COMMIT TRANSACTION", expect("COMMIT TRANSACTION", null, null, "COMMIT TRANSACTION")),
        Arguments.of("ROLLBACK", expect("ROLLBACK", null, null, "ROLLBACK")),
        Arguments.of(
            "ROLLBACK TRANSACTION",
            expect("ROLLBACK TRANSACTION", null, null, "ROLLBACK TRANSACTION")),

        // LOCK statement
        Arguments.of(
            "LOCK TABLE users", expect("LOCK TABLE users", null, null, "LOCK TABLE users")),
        Arguments.of(
            "LOCK TABLE users IN EXCLUSIVE MODE",
            expect("LOCK TABLE users IN EXCLUSIVE MODE", null, null, "LOCK TABLE users")),
        Arguments.of(
            "LOCK TABLES users WRITE, orders READ",
            expect("LOCK TABLES users WRITE, orders READ", null, null, "LOCK TABLES users")),

        // USE statement
        Arguments.of("USE mydb", expect("USE mydb", null, null, "USE mydb")),
        Arguments.of(
            "USE `my database`", expect("USE `my database`", null, null, "USE `my database`")),

        // GRANT statement
        Arguments.of(
            "GRANT SELECT ON users TO some_user",
            expect("GRANT SELECT ON users TO some_user", "SELECT", null, "GRANT")),
        Arguments.of(
            "GRANT ALL PRIVILEGES ON database.* TO 'user'@'host'",
            expect("GRANT ALL PRIVILEGES ON database.* TO ?@?", null, null, "GRANT")),

        // REVOKE statement
        Arguments.of(
            "REVOKE SELECT ON users FROM some_user",
            expect("REVOKE SELECT ON users FROM some_user", "SELECT", "some_user", "REVOKE")),
        Arguments.of(
            "REVOKE ALL PRIVILEGES ON database.* FROM 'user'@'host'",
            expect("REVOKE ALL PRIVILEGES ON database.* FROM ?@?", "SELECT", null, "REVOKE")),

        // SHOW statement
        Arguments.of("SHOW TABLES", expect("SHOW TABLES", null, null, "SHOW")),
        Arguments.of(
            "SHOW CREATE TABLE users",
            expect("SHOW CREATE TABLE users", "CREATE TABLE", "users", "SHOW")),
        Arguments.of("SHOW DATABASES", expect("SHOW DATABASES", null, null, "SHOW")),

        // SQL keywords used as identifiers (table names)
        // Note: old semconv path (collectionName) doesn't handle keywords as identifiers
        Arguments.of(
            "SELECT * FROM insert WHERE x = 1",
            expect("SELECT * FROM insert WHERE x = ?", "SELECT", "WHERE", "SELECT insert")),
        Arguments.of("SELECT * FROM update", expect("SELECT", null, "SELECT update")),
        Arguments.of("SELECT * FROM delete", expect("SELECT", null, "SELECT delete")),
        Arguments.of("SELECT * FROM call", expect("SELECT", null, "SELECT call")),
        Arguments.of("SELECT * FROM merge", expect("SELECT", null, "SELECT merge")),
        Arguments.of("SELECT * FROM create", expect("SELECT", null, "SELECT create")),
        Arguments.of("SELECT * FROM drop", expect("SELECT", null, "SELECT drop")),
        Arguments.of("SELECT * FROM alter", expect("SELECT", null, "SELECT alter")),
        Arguments.of("SELECT * FROM exec", expect("SELECT", "exec", "SELECT exec")),
        Arguments.of("SELECT * FROM execute", expect("SELECT", "execute", "SELECT execute")),

        // Oracle database link syntax (table@dblink)
        Arguments.of(
            "SELECT * FROM users@remote_db", expect("SELECT", "users", "SELECT users@remote_db")),
        Arguments.of(
            "SELECT * FROM schema.users@remote_db",
            expect("SELECT", "schema.users", "SELECT schema.users@remote_db")),

        // SQL keywords used as column names
        Arguments.of(
            "SELECT insert, update FROM mytable", expect("SELECT", "mytable", "SELECT mytable")),

        // SQL keywords used as table aliases
        Arguments.of("SELECT * FROM mytable insert", expect("SELECT", "mytable", "SELECT mytable")),
        Arguments.of(
            "SELECT * FROM mytable AS update", expect("SELECT", "mytable", "SELECT mytable")),

        // CTEs (Common Table Expressions) - CTE names are filtered from query summary
        Arguments.of(
            "WITH cte AS (SELECT a FROM b) SELECT * FROM cte",
            expect("SELECT", null, "SELECT b SELECT")),
        Arguments.of(
            "WITH cte AS (VALUES (1, 'a'), (2, 'b')) SELECT * FROM cte",
            expect(
                "WITH cte AS (VALUES (?, ?), (?, ?)) SELECT * FROM cte",
                "SELECT",
                "cte",
                "SELECT")),
        // Multiple CTEs - CTE references filtered in main query
        Arguments.of(
            "WITH a AS (SELECT * FROM t1), b AS (SELECT * FROM t2) SELECT * FROM a JOIN b ON a.id = b.id",
            expect("SELECT", null, "SELECT t1 SELECT t2 SELECT")),
        // Recursive CTE - self-reference filtered in CTE body and main query
        Arguments.of(
            "WITH RECURSIVE cte AS (SELECT id FROM t WHERE parent IS NULL UNION ALL SELECT t.id FROM t JOIN cte ON t.parent = cte.id) SELECT * FROM cte",
            expect("SELECT", null, "SELECT t SELECT t SELECT")));
  }

  private static Stream<Arguments> simplifyDefaultArgs() {
    return Stream.of(
        Arguments.of(
            "select \"a\" IN(x, \"b\") from table where col in (1) and z IN( \"3\", \"4\" )",
            expect(
                "select ? IN(x, ?) from table where col in (?) and z IN(?)",
                "SELECT",
                "table",
                "SELECT table")),
        Arguments.of(
            "update `my table` set answer=42 where x IN(\"a\", \"b\") AND y In (\"a\", \"b\")",
            expect(
                "update `my table` set answer=? where x IN(?) AND y In (?)",
                "UPDATE",
                "my table",
                "UPDATE `my table`")));
  }

  private static Stream<Arguments> ddlArgs() {
    return Stream.of(
        Arguments.of(
            "CREATE TABLE `table`", expect("CREATE TABLE", "table", "CREATE TABLE `table`")),
        Arguments.of(
            "CREATE TABLE IF NOT EXISTS table",
            expect("CREATE TABLE", "table", "CREATE TABLE table")),
        Arguments.of("DROP TABLE `if`", expect("DROP TABLE", "if", "DROP TABLE `if`")),
        Arguments.of(
            "ALTER TABLE table ADD CONSTRAINT c FOREIGN KEY (foreign_id) REFERENCES ref (id)",
            expect("ALTER TABLE", "table", "ALTER TABLE table")),
        Arguments.of(
            "CREATE INDEX types_name ON types (name)",
            expect("CREATE INDEX", null, "CREATE INDEX types_name")),
        Arguments.of(
            "DROP INDEX types_name ON types (name)",
            expect("DROP INDEX", null, "DROP INDEX types_name")),
        Arguments.of(
            "CREATE VIEW tmp AS SELECT type FROM table WHERE id = ?",
            expect("CREATE VIEW", null, "CREATE VIEW tmp SELECT table")),
        Arguments.of(
            "CREATE PROCEDURE p AS SELECT * FROM table GO",
            expect("CREATE PROCEDURE", null, "CREATE PROCEDURE p SELECT table")),
        // ALTER TABLE with DROP/ADD clauses
        Arguments.of(
            "ALTER TABLE t2 DROP COLUMN c, DROP COLUMN d",
            expect("ALTER TABLE", "t2", "ALTER TABLE t2")),
        Arguments.of(
            "ALTER TABLE users ADD COLUMN email VARCHAR(255), DROP COLUMN legacy_id, MODIFY COLUMN status INT",
            expect(
                "ALTER TABLE users ADD COLUMN email VARCHAR(?), DROP COLUMN legacy_id, MODIFY COLUMN status INT",
                "ALTER TABLE",
                "users",
                "ALTER TABLE users")));
  }

  @ParameterizedTest
  @MethodSource("doubleQuotesAsIdentifiersArgs")
  void simplifySqlDoubleQuotesAsIdentifiers(
      String original, Function<String, SqlQuery> expectedFunction) {
    SqlQuery result = sanitize(original, DOUBLE_QUOTES_ARE_IDENTIFIERS);
    SqlQuery expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    } else {
      assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
      assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  private static Stream<Arguments> doubleQuotesAsIdentifiersArgs() {
    return Stream.of(
        // When dialect treats double quotes as identifiers, quoted text is preserved in query
        Arguments.of("SELECT * FROM \"TABLE\"", expect("SELECT", "TABLE", "SELECT \"TABLE\"")),
        Arguments.of(
            "SELECT x, y, z FROM \"schema table\"",
            expect("SELECT", "schema table", "SELECT \"schema table\"")),
        Arguments.of(
            "insert into \"db table\" where lalala",
            expect("INSERT", "db table", "INSERT \"db table\"")),
        Arguments.of(
            "delete from \"my table\" where something something",
            expect("DELETE", "my table", "DELETE \"my table\"")),
        Arguments.of(
            "update \"my table\" set answer=42",
            expect(
                "update \"my table\" set answer=?", "UPDATE", "my table", "UPDATE \"my table\"")),
        Arguments.of("merge into \"my table\"", expect("MERGE", "my table", "MERGE \"my table\"")));
  }
}
