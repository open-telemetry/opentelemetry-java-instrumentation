/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

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

class SqlStatementSanitizerTest {

  private static final SqlStatementSanitizer SANITIZER = SqlStatementSanitizer.create(true);

  private static SqlStatementInfo sanitize(String sql) {
    return emitStableDatabaseSemconv()
        ? SANITIZER.sanitizeWithSummary(sql)
        : SANITIZER.sanitize(sql);
  }

  private static SqlStatementInfo sanitize(String sql, SqlDialect dialect) {
    return emitStableDatabaseSemconv()
        ? SANITIZER.sanitizeWithSummary(sql, dialect)
        : SANITIZER.sanitize(sql, dialect);
  }

  @ParameterizedTest
  @MethodSource("sqlArgs")
  void sanitizeSql(String original, String expected, String expectedQuerySummary) {
    SqlStatementInfo result = sanitize(original);
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
    SqlStatementInfo result = sanitize(original, SqlDialect.COUCHBASE);
    assertThat(result.getQueryText()).isEqualTo(expected);
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
    } else {
      assertThat(result.getQuerySummary()).isNull();
    }
  }

  @ParameterizedTest
  @MethodSource("simplifyArgs")
  void simplifySql(String original, Function<String, SqlStatementInfo> expectedFunction) {
    SqlStatementInfo result = sanitize(original);
    SqlStatementInfo expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    if (emitStableDatabaseSemconv()) {
      assertThat(result.getOperationName()).isNull();
      assertThat(result.getCollectionName()).isNull();
      assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    } else {
      assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
      assertThat(result.getCollectionName()).isEqualToIgnoringCase(expected.getCollectionName());
      assertThat(result.getQuerySummary()).isNull();
    }
    assertThat(result.getStoredProcedureName())
        .isEqualToIgnoringCase(expected.getStoredProcedureName());
  }

  @ParameterizedTest
  @MethodSource("sensitiveArgs")
  void sanitizeSensitive(String original, String expected, String expectedQuerySummary) {
    SqlStatementInfo result = sanitize(original);
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

    SqlStatementInfo result = sanitize(query);

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
  void checkDdlOperationStatementsAreOk(
      String actual, Function<String, SqlStatementInfo> expectFunc) {
    SqlStatementInfo result = sanitize(actual);
    SqlStatementInfo expected = expectFunc.apply(actual);
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
    SqlStatementSanitizer sanitizer = SqlStatementSanitizer.create(true);
    for (int i = 0; i < 10000; i++) {
      assertThat(sanitizer.sanitize(s)).isNotNull();
      s += "'";
    }
  }

  @Test
  void veryLongNumbersAreOk() {
    String s = "";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlStatementInfo result = SqlStatementSanitizer.create(true).sanitize(s);
    assertThat(result.getQueryText()).isEqualTo("?");
  }

  @Test
  void veryLongNumbersAtEndOfTableAreOk() {
    String s = "A";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlStatementInfo result = SqlStatementSanitizer.create(true).sanitize(s);
    assertThat(result.getQueryText()).isEqualTo(s.substring(0, AutoSqlSanitizer.LIMIT));
  }

  @Test
  void test32kTruncation() {
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    SqlStatementInfo result = sanitize(s.toString());
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
      SqlStatementSanitizer.create(true).sanitize(sb.toString());
    }
  }

  @Test
  public void longInStatementDoesntCauseStackOverflow() {
    StringBuilder s = new StringBuilder("select col from table where col in (");
    for (int i = 0; i < 10000; i++) {
      s.append("?,");
    }
    s.append("?)");

    String sanitized = SqlStatementSanitizer.create(true).sanitize(s.toString()).getQueryText();

    assertThat(sanitized).isEqualTo("select col from table where col in (?)");
  }

  @Test
  public void largeStatementCached() {
    // test that short statement is cached
    String shortStatement = "SELECT * FROM TABLE WHERE FIELD = 1234";
    String sanitizedShort =
        SqlStatementSanitizer.create(true).sanitize(shortStatement).getQueryText();
    assertThat(sanitizedShort).doesNotContain("1234");
    assertThat(SqlStatementSanitizer.isCached(shortStatement)).isTrue();

    // test that large statement is not cached
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    String largeStatement = s.toString();
    String sanitizedLarge =
        SqlStatementSanitizer.create(true).sanitize(largeStatement).getQueryText();
    assertThat(sanitizedLarge).doesNotContain("1234");
    assertThat(SqlStatementSanitizer.isCached(largeStatement)).isFalse();
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
        SqlStatementSanitizer.create(true).sanitizeWithSummary(sql.toString()).getQuerySummary();
    assertThat(result).isNotNull();
    assertThat(result)
        .isEqualTo(
            "SELECT very_long_table_name_0 very_long_table_name_1 very_long_table_name_2 very_long_table_name_3 very_long_table_name_4 very_long_table_name_5 very_long_table_name_6 very_long_table_name_7 very_long_table_name_8 very_long_table_name_9");
    assertThat(result.length()).isEqualTo(236);
  }

  @Test
  void sequenceOperationDoesNotCaptureStoredProcedureName() {
    assumeTrue(emitStableDatabaseSemconv());
    SqlStatementInfo result =
        SqlStatementSanitizer.create(true)
            .sanitizeWithSummary("call next value for hibernate_sequence");
    // TODO: sequence name capture will be improved in a later commit
    assertThat(result.getQuerySummary()).isEqualTo("CALL value");
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getStoredProcedureName()).isNull();
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
        Arguments.of("SELECT * FROM \"TABLE\"", "SELECT * FROM \"TABLE\"", "SELECT \"TABLE\""),

        // Not numbers but could be confused as such
        Arguments.of("SELECT A + B", "SELECT A + B", "SELECT"),
        Arguments.of("SELECT -- comment", "SELECT -- comment", "SELECT"),
        Arguments.of("SELECT * FROM TABLE123", "SELECT * FROM TABLE123", "SELECT TABLE123"),
        Arguments.of(
            "SELECT FIELD2 FROM TABLE_123 WHERE X<>7",
            "SELECT FIELD2 FROM TABLE_123 WHERE X<>?",
            "SELECT TABLE_123"),

        // Semi-nonsensical almost-numbers to elide or not
        Arguments.of("SELECT --83--...--8e+76e3E-1", "SELECT ?", "SELECT"),
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
        Arguments.of("FROM TABLE WHERE FIELD=1234", "FROM TABLE WHERE FIELD=?", "TABLE"));
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

  private static Function<String, SqlStatementInfo> expect(
      String operation, String collectionName, String querySummary) {
    return sql ->
        emitStableDatabaseSemconv()
            ? SqlStatementInfo.createWithSummary(sql, null, querySummary)
            : SqlStatementInfo.create(sql, operation, collectionName);
  }

  private static Function<String, SqlStatementInfo> expect(
      String sql, String operation, String collectionName, String querySummary) {
    return ignored ->
        emitStableDatabaseSemconv()
            ? SqlStatementInfo.createWithSummary(sql, null, querySummary)
            : SqlStatementInfo.create(sql, operation, collectionName);
  }

  private static Function<String, SqlStatementInfo> expectStoredProcedure(
      String operation, String storedProcedureName, String querySummary) {
    return sql ->
        emitStableDatabaseSemconv()
            ? SqlStatementInfo.createWithSummary(sql, storedProcedureName, querySummary)
            : SqlStatementInfo.create(sql, operation, storedProcedureName);
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
            expect("SELECT", "schema table", "SELECT \"schema table\"")),
        Arguments.of(
            "SELECT x, y, z FROM \"schema\".\"table\"",
            expect("SELECT", "\"schema\".\"table\"", "SELECT \"schema\".\"table\"")),
        Arguments.of(
            "WITH subquery as (select a from b) SELECT x, y, z FROM table",
            expect("SELECT", null, "SELECT b SELECT table")),
        Arguments.of(
            "SELECT x, y, (select a from b) as z FROM table",
            expect("SELECT", null, "SELECT SELECT b table")),
        // TODO: invalid SQL - may be refined in later commits
        Arguments.of(
            "select delete, insert into, merge, update from table", // invalid SQL
            expect("SELECT", "table", "SELECT DELETE INSERT MERGE UPDATE table")),
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
            "select col from table1 union select col from table2",
            expect("SELECT", null, "SELECT table1 SELECT table2")),
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
        Arguments.of("SeLeCT * FrOm TAblE", expect("SELECT", "table", "SELECT TAblE")),
        Arguments.of("select next value in hibernate_sequence", expect("SELECT", null, "SELECT")),

        // hibernate/jpa
        Arguments.of("FROM schema.table", expect("SELECT", "schema.table", "schema.table")),
        Arguments.of("/* update comment */ from table1", expect("SELECT", "table1", "table1")),

        // Insert
        Arguments.of(" insert into table where lalala", expect("INSERT", "table", "INSERT table")),
        // TODO: invalid SQL - may be refined in later commits
        Arguments.of(
            "insert insert into table where lalala", // invalid SQL
            expect("INSERT", "table", "INSERT INSERT table")),
        Arguments.of(
            "insert into db.table where lalala", expect("INSERT", "db.table", "INSERT db.table")),
        Arguments.of(
            "insert into `db table` where lalala",
            expect("INSERT", "db table", "INSERT `db table`")),
        Arguments.of(
            "insert into \"db table\" where lalala",
            expect("INSERT", "db table", "INSERT \"db table\"")),
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
            expect("DELETE", "my table", "DELETE \"my table\"")),
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
            expect(
                "update \"my table\" set answer=?", "UPDATE", "my table", "UPDATE \"my table\"")),
        Arguments.of("update /*table", expect("UPDATE", null, "UPDATE")),

        // Call - stable semconv returns CALL + procedure name for querySummary
        Arguments.of(
            "call test_proc()", expectStoredProcedure("CALL", "test_proc", "CALL test_proc")),
        Arguments.of(
            "call test_proc", expectStoredProcedure("CALL", "test_proc", "CALL test_proc")),
        // "call next value for sequence" is a sequence operation, not a stored proc call
        // TODO: sequence name capture will be improved in a later commit
        Arguments.of("call next value for hibernate_sequence", expect("CALL", null, "CALL value")),
        Arguments.of(
            "call db.test_proc",
            expectStoredProcedure("CALL", "db.test_proc", "CALL db.test_proc")),

        // Merge
        Arguments.of("merge into table", expect("MERGE", "table", "MERGE table")),
        Arguments.of("merge into `my table`", expect("MERGE", "my table", "MERGE `my table`")),
        Arguments.of("merge into \"my table\"", expect("MERGE", "my table", "MERGE \"my table\"")),
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
                "SELECT t1; INSERT t2")));
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
            expect("CREATE PROCEDURE", null, "CREATE PROCEDURE p SELECT table")));
  }
}
