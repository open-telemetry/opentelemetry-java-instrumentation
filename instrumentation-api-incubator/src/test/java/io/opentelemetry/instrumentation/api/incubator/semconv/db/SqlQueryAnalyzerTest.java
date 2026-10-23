/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_IDENTIFIERS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlQueryAnalyzerTest {

  private static final SqlQueryAnalyzer ANALYZER = SqlQueryAnalyzer.create(true);

  private static SqlQuery analyze(String sql) {
    return ANALYZER.analyze(sql, DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }

  private static SqlQuery analyze(String sql, SqlDialect dialect) {
    return ANALYZER.analyze(sql, dialect);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void nullQuery(boolean sanitizationEnabled) {
    SqlQuery result =
        SqlQueryAnalyzer.create(sanitizationEnabled)
            .analyze(null, DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText()).isNull();
    assertThat(result.getQuerySummary()).isNull();
    assertThat(result.getOperationName()).isNull();
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getStoredProcedureName()).isNull();
  }

  @Test
  void sanitizationDisabled() {
    String query = "CALL unsanitized_procedure('secret')";
    SqlQuery result =
        SqlQueryAnalyzer.create(false).analyze(query, DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText()).isEqualTo(query);
    assertThat(result.getQuerySummary()).isNull();
    assertThat(result.getOperationName()).isNull();
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getStoredProcedureName()).isNull();
    assertThat(SqlQueryAnalyzer.isCached(query, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isFalse();
  }

  @Test
  void cacheDistinguishesDialects() {
    String query = "SELECT \"dialect_value\" FROM dialect_cache_test";

    SqlQuery literals = analyze(query, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery identifiers = analyze(query, DOUBLE_QUOTES_ARE_IDENTIFIERS);

    assertThat(literals.getQueryText()).isEqualTo("SELECT ? FROM dialect_cache_test");
    assertThat(identifiers.getQueryText()).isEqualTo(query);
    assertThat(literals.getQuerySummary()).isEqualTo("SELECT dialect_cache_test");
    assertThat(identifiers.getQuerySummary()).isEqualTo("SELECT dialect_cache_test");
    assertThat(analyze(query, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isSameAs(literals);
    assertThat(analyze(query, DOUBLE_QUOTES_ARE_IDENTIFIERS)).isSameAs(identifiers);
  }

  @Test
  void cacheThreshold() {
    String prefix = "SELECT 1234 FROM cache_threshold_test";
    String query = prefix + repeat(' ', 10 * 1024 - prefix.length());
    SqlQuery result = analyze(query);

    assertThat(result.getQueryText()).isEqualTo("SELECT ? FROM cache_threshold_test ");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT cache_threshold_test");
    assertThat(SqlQueryAnalyzer.isCached(query, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isTrue();
    assertThat(analyze(query)).isSameAs(result);

    String largeQuery = query + " ";
    SqlQuery largeResult = analyze(largeQuery);
    assertThat(largeResult).isEqualTo(result);
    assertThat(SqlQueryAnalyzer.isCached(largeQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isFalse();
    assertThat(analyze(largeQuery)).isEqualTo(largeResult).isNotSameAs(largeResult);
  }

  @ParameterizedTest
  @MethodSource("sqlArgs")
  void sanitizeSql(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = analyze(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo(expected);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  @ParameterizedTest
  @MethodSource("simplifyArgs")
  void simplifySql(String original, Function<String, SqlQuery> expectedFunction) {
    SqlQuery result = analyze(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
    assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
    assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    assertThat(result.getStoredProcedureName()).isEqualTo(expected.getStoredProcedureName());
  }

  @ParameterizedTest
  @MethodSource("sensitiveArgs")
  void sanitizeSensitive(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = analyze(original);
    assertThat(result.getQueryText()).isEqualTo(expected);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  @Test
  void sanitizeGrantObjectNamedPassword() {
    SqlQuery result =
        ANALYZER.analyze("GRANT SELECT ON password TO admin", DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText()).isEqualTo("GRANT SELECT ON password TO admin");
    assertThat(result.getQuerySummary()).isEqualTo("GRANT");
  }

  @Test
  void sanitizeGrantIdentifiedByAfterPreviousStatement() {
    SqlQuery result =
        ANALYZER.analyze(
            "SELECT 1; GRANT ALL PRIVILEGES ON database.* TO user IDENTIFIED BY Password1",
            DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText())
        .isEqualTo("SELECT ?; GRANT ALL PRIVILEGES ON database.* TO user IDENTIFIED BY ?");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT; GRANT");
  }

  @Test
  void sanitizeDdlObjectNamedPassword() {
    SqlQuery result =
        ANALYZER.analyze(
            "CREATE USER password PASSWORD Password1", DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText()).isEqualTo("CREATE USER password PASSWORD ?");
    assertThat(result.getQuerySummary()).isEqualTo("CREATE USER password");
  }

  private static Stream<Arguments> sensitiveArgs() {
    return Stream.of(
        // SAP HANA CREATE USER statements can contain unquoted password
        // https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20d3b9ad751910148cdccc8205563a87.html?locale=en-US
        Arguments.of(
            "CREATE USER new_user PASSWORD Password1",
            "CREATE USER new_user PASSWORD ?",
            "CREATE USER new_user"),
        Arguments.of(
            "ALTER USER user PASSWORD Password1", "ALTER USER user PASSWORD ?", "ALTER USER user"),
        // Other SAP HANA administrative statements can contain unquoted or double-quoted passwords
        // after a PASSWORD keyword.
        Arguments.of("CONNECT user PASSWORD Password1", "CONNECT user PASSWORD ?", null),
        Arguments.of(
            "VALIDATE USER user PASSWORD Password1", "VALIDATE USER user PASSWORD ?", null),
        Arguments.of("CHECK USER user PASSWORD \"Password1\"", "CHECK USER user PASSWORD ?", null),
        Arguments.of(
            "CREATE DATABASE db SYSTEM USER PASSWORD Password1",
            "CREATE DATABASE db SYSTEM USER PASSWORD ?",
            "CREATE DATABASE db"),
        Arguments.of(
            "ALTER SYSTEM SET SECURE STORE BACKUP PASSWORD \"Password1\"",
            "ALTER SYSTEM SET SECURE STORE BACKUP PASSWORD ?",
            "ALTER SYSTEM"),
        Arguments.of(
            "EXPORT table AS BINARY ENCRYPTION PASSWORD Password1",
            "EXPORT table AS BINARY ENCRYPTION PASSWORD ?",
            null),
        Arguments.of(
            "IMPORT MY_SCHEMA1.T1 FROM '/backup/export_dir' WITH ENCRYPTION PASSWORD Password1",
            "IMPORT MY_SCHEMA1.T1 FROM ? WITH ENCRYPTION PASSWORD ?",
            null),
        Arguments.of(
            "RECOVER ENCRYPTION ROOT KEYS USING 'backup' PASSWORD Password1",
            "RECOVER ENCRYPTION ROOT KEYS USING ? PASSWORD ?",
            null),
        Arguments.of(
            "GRANT SELECT ON users TO admin /* PASSWORD Password1 */",
            "GRANT SELECT ON users TO admin /* PASSWORD Password1 */",
            "GRANT"),
        // Oracle CREATE USER statement can contain unquoted password
        // https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_8003.htm
        Arguments.of(
            "CREATE USER new_user IDENTIFIED BY Password1",
            "CREATE USER new_user IDENTIFIED BY ?",
            "CREATE USER new_user"),
        Arguments.of(
            "ALTER USER user IDENTIFIED BY Password1 REPLACE Password2",
            "ALTER USER user IDENTIFIED BY ?",
            "ALTER USER user"),
        Arguments.of(
            "GRANT ALL PRIVILEGES ON database.* TO user IDENTIFIED BY Password1",
            "GRANT ALL PRIVILEGES ON database.* TO user IDENTIFIED BY ?",
            "GRANT"),
        Arguments.of(
            "GRANT SELECT ON users TO admin; SELECT password FROM users",
            "GRANT SELECT ON users TO admin; SELECT password FROM users",
            "GRANT; SELECT users"),
        // field names do not trigger sensitive statement sanitization
        Arguments.of("SELECT connect FROM TABLE", "SELECT connect FROM TABLE", "SELECT TABLE"),
        Arguments.of(
            "SELECT grant, password FROM users",
            "SELECT grant, password FROM users",
            "SELECT users"),
        Arguments.of("SELECT password FROM users", "SELECT password FROM users", "SELECT users"),
        Arguments.of(
            "SELECT identified_by FROM users", "SELECT identified_by FROM users", "SELECT users"),
        Arguments.of(
            "TRUNCATE TABLE password", "TRUNCATE TABLE password", "TRUNCATE TABLE password"),
        Arguments.of(
            "REPLACE password VALUES (1)", "REPLACE password VALUES (?)", "REPLACE password"),
        Arguments.of("VALUES (password)", "VALUES (password)", "VALUES"),
        Arguments.of(
            "UPDATE users SET password = \"Password1\"",
            "UPDATE users SET password = ?",
            "UPDATE users"));
  }

  @ParameterizedTest
  @MethodSource("blockCommentArgs")
  void sanitizeBlockComments(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = analyze(original);

    assertThat(result.getQueryText()).isEqualTo(expected);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  private static Stream<Arguments> blockCommentArgs() {
    return Stream.of(
        argumentSet(
            "nested comment before an apostrophe",
            "SELECT /* outer /* inner */ don't */ 'secret'",
            "SELECT /* outer ?",
            "SELECT"),
        argumentSet(
            "multiple nested comments before the operation",
            "/* outer /* middle /* inner */ */ */ SELECT 'secret'",
            "/* outer ?",
            null),
        argumentSet(
            "nested opener inside a quoted comment value",
            "SELECT /* 'text /* inner */ text' */ 'secret'",
            "SELECT /* 'text ?",
            "SELECT"),
        argumentSet(
            "non-nesting dialect closes at the first terminator",
            "SELECT /* outer /* inner */ 'text */ secret'",
            "SELECT /* outer ?",
            "SELECT"),
        argumentSet(
            "line comment marker inside a block comment",
            "SELECT /* -- ignored */ 'secret'",
            "SELECT /* -- ignored */ ?",
            "SELECT"),
        argumentSet(
            "double quote before the comment terminator",
            "SELECT /* \"ignored */ 'secret' AS \"alias\"",
            "SELECT /* \"ignored */ ? AS ?",
            "SELECT"),
        argumentSet(
            "quoted identifier before the comment terminator",
            "SELECT /* schema.\"ignored */ 'secret' AS \"alias\"",
            "SELECT /* schema.\"ignored */ ? AS ?",
            "SELECT"),
        argumentSet(
            "backtick before the comment terminator",
            "SELECT /* `ignored */ 'secret' AS `alias`",
            "SELECT /* `ignored */ ? AS `alias`",
            "SELECT"),
        argumentSet(
            "bracket before the comment terminator",
            "SELECT /* [ignored */ 'secret' AS [alias]",
            "SELECT /* [ignored */ ? AS [alias]",
            "SELECT"),
        argumentSet(
            "dollar quotes before the comment terminator",
            "SELECT /* $$ ignored */ 'secret', $$other$$",
            "SELECT /* $$ ignored */ ?, ?",
            "SELECT"),
        argumentSet(
            "dollar tag before the comment terminator",
            "SELECT /* $tag$ ignored */ 'secret'",
            "SELECT /* ?*/ ?",
            "SELECT"),
        argumentSet(
            "nested opener inside a dollar-quoted comment value",
            "SELECT /* $tag$ /* inner */ $tag$ */ 'secret'",
            "SELECT /* ?",
            "SELECT"),
        argumentSet(
            "sanitized comment values",
            "SELECT /* user='secret' id=42 value=$tag$secret$tag$ */ 'secret' FROM users",
            "SELECT /* user=? id=? value=? */ ? FROM users",
            "SELECT users"),
        argumentSet(
            "comment delimiters inside a literal",
            "SELECT '/* outer /* inner */ */' FROM users",
            "SELECT ? FROM users",
            "SELECT users"),
        argumentSet(
            "comment delimiters inside a dollar-quoted literal",
            "SELECT $tag$/* outer /* inner */ */$tag$ FROM users",
            "SELECT ? FROM users",
            "SELECT users"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1024, 4096, 8192, 16384})
  void bracketsInCommentDoNotHideTerminator(int count) {
    String comment = "/* " + repeat('[', count) + " */";

    SqlQuery result = analyze("SELECT " + comment + " 'secret' AS [alias]");

    assertThat(result.getQueryText()).isEqualTo("SELECT " + comment + " ? AS [alias]");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT");
  }

  @ParameterizedTest
  @ValueSource(ints = {1024, 4096, 8192, 16384})
  void unterminatedBracketIdentifiersAreRedacted(int count) {
    SqlQuery result = analyze("SELECT * FROM " + repeat('[', count) + " 'secret'");

    assertThat(result.getQueryText()).isEqualTo("SELECT * FROM ?");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT");
    assertThat(result.getCollectionName()).isNull();
  }

  @ParameterizedTest
  @MethodSource("bracketIdentifierArgs")
  void sanitizeBracketIdentifiers(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = analyze(original);

    assertThat(result.getQueryText()).isEqualTo(expected);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  private static Stream<Arguments> bracketIdentifierArgs() {
    return Stream.of(
        argumentSet("empty unfinished identifier", "SELECT [", "SELECT ?", "SELECT"),
        argumentSet(
            "unfinished identifier contains a literal",
            "SELECT [unfinished 'secret'",
            "SELECT ?",
            "SELECT"),
        argumentSet(
            "unfinished identifier after a complete statement",
            "SELECT * FROM users; SELECT [unfinished 'secret'",
            "SELECT * FROM users; SELECT ?",
            "SELECT users; SELECT"),
        argumentSet(
            "closing bracket takes precedence",
            "SELECT [[column], 'secret' FROM [users]",
            "SELECT [[column], ? FROM [users]",
            "SELECT [users]"),
        argumentSet(
            "qualified bracket identifiers",
            "SELECT * FROM [schema].[[table] WHERE value = 'secret'",
            "SELECT * FROM [schema].[[table] WHERE value = ?",
            "SELECT [schema].[[table]"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1024, 4096, 8192, 16384})
  void numericPrefixesInCommentDoNotHideTerminator(int count) {
    String comment = "/* " + repeat('+', count) + " */";

    SqlQuery result = analyze("SELECT " + comment + " 'secret' FROM users");

    assertThat(result.getQueryText()).isEqualTo("SELECT " + comment + " ? FROM users");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT users");
  }

  @ParameterizedTest
  @MethodSource("numericPrefixArgs")
  void sanitizeNumericPrefixes(String original, String expected, String expectedQuerySummary) {
    SqlQuery result = analyze(original);

    assertThat(result.getQueryText()).isEqualTo(expected);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  private static Stream<Arguments> numericPrefixArgs() {
    return Stream.of(
        argumentSet(
            "numeric prefix without a number",
            "SELECT .+-.+- 'secret' FROM users",
            "SELECT .+-.+- ? FROM users",
            "SELECT users"),
        argumentSet(
            "numeric prefix followed by a number",
            "SELECT +++123, 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "number inside a comment",
            "SELECT /* +++123 */ 'secret' FROM users",
            "SELECT /* ? */ ? FROM users",
            "SELECT users"),
        argumentSet(
            "line comment embedded in a numeric prefix",
            "SELECT +-- FROM ignored\n'secret' FROM users",
            "SELECT +-- FROM ignored ? FROM users",
            "SELECT users"),
        argumentSet(
            "line comment without a space after its marker",
            "SELECT +--ignored\n'secret'",
            "SELECT +--ignored ?",
            "SELECT"),
        argumentSet(
            "mixed numeric prefix before a line comment",
            "SELECT .+-+-- FROM ignored\r\n'secret' FROM users",
            "SELECT .+-+-- FROM ignored ? FROM users",
            "SELECT users"),
        argumentSet(
            "line comment marker inside a block comment",
            "SELECT /* +-.-+-- ignored */ 'secret' FROM users",
            "SELECT /* +-.-+-- ignored */ ? FROM users",
            "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("backslashArgs")
  void sanitizeBackslashes(String original, String expected, String expectedQuerySummary) {
    for (SqlDialect dialect :
        new SqlDialect[] {DOUBLE_QUOTES_ARE_STRING_LITERALS, DOUBLE_QUOTES_ARE_IDENTIFIERS}) {
      SqlQuery result = analyze(original, dialect);

      assertThat(result.getQueryText()).isEqualTo(expected);
      assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
    }
  }

  private static Stream<Arguments> backslashArgs() {
    return Stream.of(
        argumentSet(
            "backslash-escaped apostrophe",
            "SELECT name FROM users WHERE name = 'O\\'Brien' AND password = 'secret'",
            "SELECT name FROM users WHERE name = ?",
            "SELECT users"),
        argumentSet(
            "standard string ending with a backslash",
            "SELECT name FROM users WHERE path = 'C:\\' AND password = 'secret'",
            "SELECT name FROM users WHERE path = ?",
            "SELECT users"),
        argumentSet(
            "insert with a backslash-escaped apostrophe",
            "INSERT INTO users(name, password) VALUES('O\\'Brien', 'secret')",
            "INSERT INTO users(name, password) VALUES(?",
            "INSERT users"),
        argumentSet(
            "PostgreSQL escape string", "SELECT E'O\\'Brien', 'secret'", "SELECT E?", "SELECT"),
        argumentSet(
            "PostgreSQL escape string continuation",
            "SELECT e'first'\n'O\\'Brien' FROM users",
            "SELECT e? ?",
            "SELECT"),
        argumentSet(
            "backslash-escaped double quote",
            "SELECT name FROM users WHERE name = \"O\\\"Brien\" AND password = 'secret'",
            "SELECT name FROM users WHERE name = ?",
            "SELECT users"),
        argumentSet(
            "double-quoted standard string ending with a backslash",
            "SELECT name FROM users WHERE path = \"C:\\\" AND password = 'secret'",
            "SELECT name FROM users WHERE path = ?",
            "SELECT users"),
        argumentSet(
            "ambiguous quote in a qualified identifier",
            "SELECT * FROM schema.\"O\\\"Brien' WHERE password='secret\"",
            "SELECT * FROM ?",
            "SELECT"),
        argumentSet(
            "backslash-escaped backtick",
            "SELECT * FROM `O\\`Brien' WHERE password='secret`",
            "SELECT * FROM ?",
            "SELECT"),
        argumentSet(
            "odd backslash run", "SELECT 'C:\\\\\\', 'secret' FROM users", "SELECT ?", "SELECT"),
        argumentSet(
            "backslash before doubled apostrophes",
            "SELECT 'a\\''b', 'secret' FROM users",
            "SELECT ?",
            "SELECT"),
        argumentSet(
            "ambiguous quote in a comment value",
            "SELECT /* value='O\\'Brien' password='secret' */ 'secret'",
            "SELECT /* value=?",
            "SELECT"),
        argumentSet(
            "SQL keywords inside an escaped literal",
            "SELECT 'O\\' FROM secret_table WHERE value = ' FROM public_table",
            "SELECT ?",
            "SELECT"),
        argumentSet(
            "backslash away from a quote",
            "SELECT 'C:\\folder', 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "even backslash run",
            "SELECT 'C:\\\\', 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "longer even backslash run",
            "SELECT 'C:\\\\\\\\', 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "even backslash run before doubled apostrophes",
            "SELECT 'a\\\\''b', 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "backslash before a different quote",
            "SELECT 'a\\\"b', 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "backslashes inside dollar quotes",
            "SELECT $tag$O\\'Brien$tag$, 'secret' FROM users",
            "SELECT ?, ? FROM users",
            "SELECT users"),
        argumentSet(
            "backslash inside a bracket-quoted identifier",
            "SELECT [C:\\] FROM [users]",
            "SELECT [C:\\] FROM [users]",
            "SELECT [users]"));
  }

  @Test
  void ambiguousQuotingDoesNotPopulateTargets() {
    SqlQuery result = analyze("SELECT 'O\\' FROM secret_table WHERE value = ' FROM public_table");

    assertThat(result.getQueryText()).isEqualTo("SELECT ?");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT");
    assertThat(result.getCollectionName()).isNull();
    assertThat(result.getStoredProcedureName()).isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void unambiguousDoubleQuotesRetainDialectBehavior(boolean identifiers) {
    SqlQuery result =
        analyze(
            "SELECT * FROM \"C:\\\\\"",
            identifiers ? DOUBLE_QUOTES_ARE_IDENTIFIERS : DOUBLE_QUOTES_ARE_STRING_LITERALS);

    assertThat(result.getQueryText())
        .isEqualTo(identifiers ? "SELECT * FROM \"C:\\\\\"" : "SELECT * FROM ?");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT \"C:\\\\\"");
  }

  @Test
  void veryLongSelectStatementsAreOk() {
    StringBuilder sb = new StringBuilder("SELECT * FROM table WHERE");
    for (int i = 0; i < 2000; i++) {
      sb.append(" column").append(i).append("=123 and");
    }
    String query = sb.toString();

    String analyzedQuery = query.replace("=123", "=?").substring(0, AutoSqlSanitizer.LIMIT);

    SqlQuery result = analyze(query);

    assertThat(result.getQueryText()).isEqualTo(analyzedQuery);
    assertThat(result.getOperationName()).isEqualTo("SELECT");
    assertThat(result.getCollectionName()).isEqualTo("table");
    assertThat(result.getQuerySummary()).isEqualTo("SELECT table");
  }

  @ParameterizedTest
  @MethodSource("ddlArgs")
  void checkDdlOperationStatementsAreOk(String actual, Function<String, SqlQuery> expectFunc) {
    SqlQuery result = analyze(actual, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    SqlQuery expected = expectFunc.apply(actual);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
    assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
    assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
    assertThat(result.getStoredProcedureName()).isEqualTo(expected.getStoredProcedureName());
  }

  @Test
  void lotsOfTicksDontCauseStackOverflowOrLongRuntimes() {
    String s = "'";
    SqlQueryAnalyzer analyzer = SqlQueryAnalyzer.create(true);
    for (int i = 0; i < 10000; i++) {
      assertThat(analyzer.analyze(s, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isNotNull();
      s += "'";
    }
  }

  @Test
  void veryLongNumbersAreOk() {
    String s = "";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlQuery result = SqlQueryAnalyzer.create(true).analyze(s, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo("?");
  }

  @Test
  void veryLongNumbersAtEndOfTableAreOk() {
    String s = "A";
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i);
    }
    SqlQuery result = SqlQueryAnalyzer.create(true).analyze(s, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQueryText()).isEqualTo(s.substring(0, AutoSqlSanitizer.LIMIT));
  }

  @Test
  void test32kTruncation() {
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    SqlQuery result = analyze(s.toString());
    assertThat(result.getQueryText().length()).isLessThanOrEqualTo(AutoSqlSanitizer.LIMIT);
    assertThat(result.getQueryText()).doesNotContain("1234");
    assertThat(result.getQuerySummary()).startsWith("SELECT TABLE SELECT TABLE SELECT TABLE");
  }

  @Test
  void queryTextTruncationDoesNotSplitSurrogatePair() {
    String beforePair = repeat('A', AutoSqlSanitizer.LIMIT - 1);

    SqlQuery result = analyze(beforePair + "😀");

    assertThat(result.getQueryText()).isEqualTo(beforePair);
  }

  @Test
  void randomBytesDontCauseExceptionsOrTimeouts() {
    Random r = new Random(0);
    for (int i = 0; i < 1000; i++) {
      StringBuffer sb = new StringBuffer();
      for (int c = 0; c < 1000; c++) {
        sb.append((char) r.nextInt(Character.MAX_VALUE));
      }
      SqlQueryAnalyzer.create(true).analyze(sb.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS);
    }
  }

  @Test
  void longInStatementDoesntCauseStackOverflow() {
    StringBuilder s = new StringBuilder("select col from table where col in (");
    for (int i = 0; i < 10000; i++) {
      s.append("?,");
    }
    s.append("?)");

    String analyzed =
        SqlQueryAnalyzer.create(true)
            .analyze(s.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();

    assertThat(analyzed).isEqualTo("select col from table where col in (?)");
  }

  @Test
  void largeQueryCached() {
    // test that short query is cached
    String shortQuery = "SELECT * FROM TABLE WHERE FIELD = 1234";
    String analyzedShort =
        SqlQueryAnalyzer.create(true)
            .analyze(shortQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();
    assertThat(analyzedShort).doesNotContain("1234");
    assertThat(SqlQueryAnalyzer.isCached(shortQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isTrue();

    // test that large query is not cached
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ");
    }
    String largeQuery = s.toString();
    String analyzedLarge =
        SqlQueryAnalyzer.create(true)
            .analyze(largeQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQueryText();
    assertThat(analyzedLarge).doesNotContain("1234");
    assertThat(SqlQueryAnalyzer.isCached(largeQuery, DOUBLE_QUOTES_ARE_STRING_LITERALS)).isFalse();
  }

  @Test
  void querySummaryIsTruncated() {
    // Build a query with many tables to exceed 255 character limit
    StringBuilder sql = new StringBuilder("SELECT * FROM ");
    for (int i = 0; i < 50; i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append("very_long_table_name_").append(i);
    }
    String result =
        SqlQueryAnalyzer.create(true)
            .analyze(sql.toString(), DOUBLE_QUOTES_ARE_STRING_LITERALS)
            .getQuerySummary();
    assertThat(result).isNotNull();
    assertThat(result)
        .isEqualTo(
            "SELECT very_long_table_name_0 very_long_table_name_1 very_long_table_name_2 very_long_table_name_3 very_long_table_name_4 very_long_table_name_5 very_long_table_name_6 very_long_table_name_7 very_long_table_name_8 very_long_table_name_9");
    assertThat(result.length()).isEqualTo(236);
  }

  @Test
  void querySummaryTruncationDoesNotSplitSurrogatePair() {
    String beforePair = repeat('A', 254);

    String summary = SqlQuery.create(null, null, null, null, beforePair + "😀").getQuerySummary();

    assertThat(summary).isEqualTo(beforePair);
  }

  @ParameterizedTest
  @MethodSource("operationCaseArgs")
  void querySummaryPreservesOperationCase(String original, String expectedQuerySummary) {
    SqlQuery result = analyze(original, DOUBLE_QUOTES_ARE_STRING_LITERALS);
    assertThat(result.getQuerySummary()).isEqualTo(expectedQuerySummary);
  }

  private static Stream<Arguments> operationCaseArgs() {
    return Stream.of(
        Arguments.of("select * from users", "select users"),
        Arguments.of("SeLeCT * FrOm users", "SeLeCT users"),
        Arguments.of("insert into users values (1)", "insert users"),
        Arguments.of("delete from users where id = 1", "delete users"),
        Arguments.of("update users set id = 1", "update users"),
        Arguments.of("call do_work()", "call do_work"),
        Arguments.of("merge into users", "merge users"),
        Arguments.of("create table users (id int)", "create table users"),
        Arguments.of("drop table users", "drop table users"),
        Arguments.of("alter table users add column id int", "alter table users"),
        Arguments.of("values (1)", "values"),
        Arguments.of("exec do_work", "exec do_work"),
        Arguments.of("execute db.do_work @param=1", "execute db.do_work"),
        Arguments.of("truncate table users", "truncate table users"),
        Arguments.of("replace into users values (1)", "replace users"),
        Arguments.of("lock table users", "lock table users"),
        Arguments.of("use mydb", "use mydb"),
        Arguments.of("begin transaction", "begin transaction"),
        Arguments.of("commit transaction", "commit transaction"),
        Arguments.of("rollback transaction", "rollback transaction"),
        Arguments.of("grant select on users to admin", "grant"),
        Arguments.of("revoke select on users from admin", "revoke"),
        Arguments.of("show create table users", "show"),
        Arguments.of("explain select * from users", "explain select users"));
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

        // An apostrophe in a block comment doesn't start a string
        Arguments.of(
            "SELECT name FROM users /* don't cache */ WHERE password = 'hunter2'",
            "SELECT name FROM users /* don't cache */ WHERE password = ?",
            "SELECT users"),
        Arguments.of(
            "SELECT a FROM t /* it's */ WHERE b = 'x' AND c = 'y'",
            "SELECT a FROM t /* it's */ WHERE b = ? AND c = ?",
            "SELECT t"),
        Arguments.of(
            "SELECT a FROM t /* user='bob' */ WHERE b = 'x'",
            "SELECT a FROM t /* user=? */ WHERE b = ?",
            "SELECT t"),

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
        Arguments.of("from TABLE WHERE FIELD=1234", "from TABLE WHERE FIELD=?", "select TABLE"));
  }

  private static Function<String, SqlQuery> expect(
      String operation, String collectionName, String querySummary) {
    return sql -> SqlQuery.create(sql, operation, collectionName, null, querySummary);
  }

  private static Function<String, SqlQuery> expect(
      String sql, String operation, String collectionName, String querySummary) {
    return ignored -> SqlQuery.create(sql, operation, collectionName, null, querySummary);
  }

  private static Function<String, SqlQuery> expectStoredProcedure(
      String operation, String storedProcedureName, String querySummary) {
    return sql -> SqlQuery.create(sql, operation, null, storedProcedureName, querySummary);
  }

  private static Function<String, SqlQuery> expectStoredProcedure(
      String sql, String operation, String storedProcedureName, String querySummary) {
    return ignored -> SqlQuery.create(sql, operation, null, storedProcedureName, querySummary);
  }

  private static Stream<Arguments> simplifyArgs() {
    return Stream.of(
        // Select
        Arguments.of(
            "SELECT x, y, z FROM schema.table",
            expect("SELECT", "schema.table", "SELECT schema.table")),
        Arguments.of(
            "SELECT x, y, z FROM `schema table`",
            expect("SELECT", "`schema table`", "SELECT `schema table`")),
        Arguments.of(
            "SELECT x, y, z FROM `schema`.`table`",
            expect("SELECT", "`schema`.`table`", "SELECT `schema`.`table`")),
        Arguments.of(
            "SELECT x, y, z FROM \"schema table\"",
            expect(
                "SELECT x, y, z FROM ?", "SELECT", "\"schema table\"", "SELECT \"schema table\"")),
        // double-quoted dot-separated identifiers are matched as IDENTIFIER, not DOUBLE_QUOTED_STR,
        // so they are always preserved regardless of dialect
        Arguments.of(
            "SELECT x, y, z FROM \"schema\".\"table\"",
            expect("SELECT", "\"schema\".\"table\"", "SELECT \"schema\".\"table\"")),
        Arguments.of(
            "WITH subquery as (select a from b) SELECT x, y, z FROM table",
            expect("select", "b", "select b SELECT table")),
        Arguments.of(
            "SELECT x, y, (select a from b) as z FROM table",
            expect("SELECT", "b", "SELECT select b table")),
        Arguments.of(
            "select delete, insert into, merge, update from table", // invalid SQL
            expect("select", "table", "select table")),
        Arguments.of(
            "select col /* from table2 */ from table", expect("select", "table", "select table")),
        Arguments.of(
            "select col from table join anotherTable",
            expect("select", "table", "select table anotherTable")),
        Arguments.of(
            "SELECT * FROM t1 LEFT JOIN t2 ON t1.id = t2.id JOIN t3 ON t2.x = t3.x",
            expect("SELECT", "t1", "SELECT t1 t2 t3")),
        Arguments.of(
            "select col from (select * from anotherTable)",
            expect("select", "anotherTable", "select select anotherTable")),
        Arguments.of(
            "select col from (select * from anotherTable) alias",
            expect("select", "anotherTable", "select select anotherTable")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) sub, t3",
            expect("SELECT", "t1", "SELECT SELECT t1 t3")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) s1, (SELECT * FROM t2) s2",
            expect("SELECT", "t1", "SELECT SELECT t1 SELECT t2")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM t1) sub, t2 JOIN t3 ON t2.id = t3.id",
            expect("SELECT", "t1", "SELECT SELECT t1 t2 t3")),
        Arguments.of(
            "select col from table1 union select col from table2",
            expect("select", "table1", "select table1 select table2")),
        Arguments.of(
            "SELECT id, name FROM employees UNION ALL SELECT id, name FROM contractors UNION SELECT id, name FROM vendors",
            expect("SELECT", "employees", "SELECT employees SELECT contractors SELECT vendors")),
        // Parenthesized table with UNION - identifier cancels pending subquery push
        Arguments.of(
            "SELECT * FROM (t UNION SELECT * FROM t2), t3",
            expect("SELECT", "t", "SELECT t SELECT t2")),
        Arguments.of(
            "select id, (select max(foo) from (select foo from foos union all select foo from bars)) as foo from main_table",
            expect("select", "foos", "select select select foos select bars main_table")),
        Arguments.of(
            "select col from table where col in (select * from anotherTable)",
            expect("select", "table", "select table select anotherTable")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM inner1 JOIN inner2 ON inner1.id = inner2.id) AS sub",
            expect("SELECT", "inner1", "SELECT SELECT inner1 inner2")),
        Arguments.of(
            "SELECT * FROM (VALUES (1,2), (3,4)) AS t(a, b)",
            expect("SELECT * FROM (VALUES (?,?), (?,?)) AS t(a, b)", "SELECT", null, "SELECT")),
        Arguments.of("SELECT * FROM t1 CROSS APPLY t2", expect("SELECT", "t1", "SELECT t1 t2")),
        Arguments.of(
            "SELECT * FROM t1 OUTER APPLY (SELECT * FROM t2 WHERE t2.id = t1.id)",
            expect("SELECT", "t1", "SELECT t1 SELECT t2")),
        Arguments.of(
            "SELECT * FROM t1, LATERAL (SELECT * FROM t2 WHERE t2.id = t1.id)",
            expect("SELECT", "t1", "SELECT t1 SELECT t2")),
        Arguments.of(
            "select col from table1, table2", expect("select", "table1", "select table1 table2")),
        Arguments.of(
            "select col from table1 t1, table2 t2",
            expect("select", "table1", "select table1 table2")),
        Arguments.of(
            "select col from table1 as t1, table2 as t2",
            expect("select", "table1", "select table1 table2")),
        Arguments.of(
            "select col from table where col in (1, 2, 3)",
            expect("select col from table where col in (?)", "select", "table", "select table")),
        Arguments.of(
            "select 'a' IN(x, 'b') from table where col in (1) and z IN( '3', '4' )",
            expect(
                "select ? IN(x, ?) from table where col in (?) and z IN(?)",
                "select",
                "table",
                "select table")),
        Arguments.of(
            "select col from table order by col, col2", expect("select", "table", "select table")),
        Arguments.of(
            "select ąś∂ń© from źćļńĶ order by col, col2",
            expect("select", "źćļńĶ", "select źćļńĶ")),
        Arguments.of("select 12345678", expect("select ?", "select", null, "select")),
        Arguments.of(
            "/* update comment */ select * from table1",
            expect("select", "table1", "select table1")),
        Arguments.of("select /*((*/abc from table", expect("select", "table", "select table")),
        Arguments.of("SeLeCT * FrOm TAblE", expect("SeLeCT", "TAblE", "SeLeCT TAblE")),
        Arguments.of("select next value in hibernate_sequence", expect("select", null, "select")),

        // EXPLAIN - preserved as prefix command in summary
        Arguments.of(
            "EXPLAIN SELECT * FROM users", expect("EXPLAIN", "users", "EXPLAIN SELECT users")),

        // hibernate/jpa
        Arguments.of("from schema.table", expect("select", "schema.table", "select schema.table")),
        Arguments.of("FROM schema.table", expect("SELECT", "schema.table", "SELECT schema.table")),
        Arguments.of(
            "/* update comment */ from table1", expect("select", "table1", "select table1")),

        // Insert
        Arguments.of(" insert into table where lalala", expect("insert", "table", "insert table")),
        Arguments.of(
            "insert into metrics.users (id) values (1)",
            expect(
                "insert into metrics.users (id) values (?)",
                "insert",
                "metrics.users",
                "insert metrics.users")),
        Arguments.of(
            "insert insert into table where lalala", // invalid SQL
            expect("insert", "table", "insert table")),
        Arguments.of(
            "insert into db.table where lalala", expect("insert", "db.table", "insert db.table")),
        Arguments.of(
            "insert into `db table` where lalala",
            expect("insert", "`db table`", "insert `db table`")),
        Arguments.of(
            "insert into \"db table\" where lalala",
            expect("insert into ? where lalala", "insert", "\"db table\"", "insert \"db table\"")),
        Arguments.of("insert without i-n-t-o", expect("insert", null, "insert")),

        // Delete
        Arguments.of(
            "delete from table where something something",
            expect("delete", "table", "delete table")),
        Arguments.of(
            "delete from `my table` where something something",
            expect("delete", "`my table`", "delete `my table`")),
        Arguments.of(
            "delete from \"my table\" where something something",
            expect(
                "delete from ? where something something",
                "delete",
                "\"my table\"",
                "delete \"my table\"")),
        Arguments.of(
            "delete from foo where x IN (1,2,3)",
            expect("delete from foo where x IN (?)", "delete", "foo", "delete foo")),
        Arguments.of("delete from 12345678", expect("delete from ?", "delete", null, "delete")),
        Arguments.of("delete   (((", expect("delete (((", "delete", null, "delete")),

        // Update
        Arguments.of(
            "update table set answer=42",
            expect("update table set answer=?", "update", "table", "update table")),
        Arguments.of(
            "update `my table` set answer=42",
            expect("update `my table` set answer=?", "update", "`my table`", "update `my table`")),
        Arguments.of(
            "update `my table` set answer=42 where x IN('a', 'b') AND y In ('a',  'b')",
            expect(
                "update `my table` set answer=? where x IN(?) AND y In (?)",
                "update",
                "`my table`",
                "update `my table`")),
        Arguments.of(
            "update \"my table\" set answer=42",
            expect("update ? set answer=?", "update", "\"my table\"", "update \"my table\"")),
        Arguments.of("update /*table", expect("update", null, "update")),

        // Call
        Arguments.of(
            "call test_proc()", expectStoredProcedure("call", "test_proc", "call test_proc")),
        Arguments.of(
            "call test_proc", expectStoredProcedure("call", "test_proc", "call test_proc")),
        // Hibernate uses "call next value for sequence" on HSQLDB and H2 to get sequence values,
        // while it uses SELECT for this on most other databases.
        Arguments.of(
            "call next value for hibernate_sequence",
            expect("call", null, "call hibernate_sequence")),
        Arguments.of(
            "call db.test_proc",
            expectStoredProcedure("call", "db.test_proc", "call db.test_proc")),

        // Merge
        Arguments.of("merge into table", expect("merge", "table", "merge table")),
        Arguments.of("merge into `my table`", expect("merge", "`my table`", "merge `my table`")),
        Arguments.of(
            "merge into \"my table\"",
            expect("merge into ?", "merge", "\"my table\"", "merge \"my table\"")),
        Arguments.of(
            "merge table (into is optional in some dbs)", expect("merge", "table", "merge table")),
        Arguments.of("merge (into )))", expect("merge", null, "merge")),

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
            expect("SELECT * FROM t1; SELECT * FROM t2", "SELECT", "t1", "SELECT t1; SELECT t2")),
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
            expect("SELECT * FROM ONLY parent", "SELECT", "parent", "SELECT parent")),
        Arguments.of(
            "DELETE FROM ONLY parent WHERE id = 1",
            expect("DELETE FROM ONLY parent WHERE id = ?", "DELETE", "parent", "DELETE parent")),
        Arguments.of(
            "UPDATE ONLY parent SET x = 1",
            expect("UPDATE ONLY parent SET x = ?", "UPDATE", "parent", "UPDATE parent")),

        // Standalone VALUES clause
        Arguments.of("VALUES (1)", expect("VALUES (?)", "VALUES", null, "VALUES")),
        Arguments.of(
            "VALUES (1, 'a'), (2, 'b')", expect("VALUES (?, ?), (?, ?)", "VALUES", null, "VALUES")),

        // EXECUTE/EXEC stored procedure calls
        Arguments.of(
            "EXEC my_procedure",
            expectStoredProcedure("EXEC", "my_procedure", "EXEC my_procedure")),
        Arguments.of(
            "EXECUTE db.my_procedure @param=1",
            expectStoredProcedure(
                "EXECUTE db.my_procedure @param=?",
                "EXECUTE",
                "db.my_procedure",
                "EXECUTE db.my_procedure")),

        // SQL Server bracket-quoted identifiers
        Arguments.of(
            "SELECT * FROM [my table]", expect("SELECT", "[my table]", "SELECT [my table]")),
        Arguments.of(
            "SELECT * FROM [schema].[table]",
            expect("SELECT", "[schema].[table]", "SELECT [schema].[table]")),
        Arguments.of(
            "SELECT [column] FROM [table] WHERE [field] = 1",
            expect(
                "SELECT [column] FROM [table] WHERE [field] = ?",
                "SELECT",
                "[table]",
                "SELECT [table]")),

        // MySQL escaped backticks
        Arguments.of(
            "SELECT * FROM `my``table`", expect("SELECT", "`my``table`", "SELECT `my``table`")),
        Arguments.of(
            "SELECT * FROM `table` WHERE `col``name` = 1",
            expect(
                "SELECT * FROM `table` WHERE `col``name` = ?",
                "SELECT",
                "`table`",
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
            "SELECT * FROM a, (SELECT * FROM b), c", expect("SELECT", "a", "SELECT a SELECT b c")),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM inner1), (SELECT * FROM inner2), outer_table",
            expect("SELECT", "inner1", "SELECT SELECT inner1 SELECT inner2 outer_table")),

        // Parenthesized table name - not a subquery - valid on MySQL
        Arguments.of("SELECT * FROM (TABLE)", expect("SELECT", "TABLE", "SELECT TABLE")),

        // TRUNCATE statement
        Arguments.of(
            "TRUNCATE TABLE users",
            expect("TRUNCATE TABLE users", "TRUNCATE TABLE", "users", "TRUNCATE TABLE users")),
        Arguments.of(
            "TRUNCATE users", expect("TRUNCATE users", "TRUNCATE", null, "TRUNCATE users")),
        Arguments.of(
            "TRUNCATE TABLE schema.table",
            expect(
                "TRUNCATE TABLE schema.table",
                "TRUNCATE TABLE",
                "schema.table",
                "TRUNCATE TABLE schema.table")),

        // REPLACE statement (MySQL)
        Arguments.of(
            "REPLACE INTO users VALUES (1, 'name')",
            expect("REPLACE INTO users VALUES (?, ?)", "REPLACE", "users", "REPLACE users")),
        Arguments.of(
            "REPLACE users SET name = 'foo'",
            expect("REPLACE users SET name = ?", "REPLACE", "users", "REPLACE users")),
        Arguments.of(
            "REPLACE INTO db.table (col) VALUES (1)",
            expect(
                "REPLACE INTO db.table (col) VALUES (?)",
                "REPLACE",
                "db.table",
                "REPLACE db.table")),

        // Transaction control statements
        Arguments.of("BEGIN", expect("BEGIN", "BEGIN", null, "BEGIN")),
        Arguments.of(
            "BEGIN TRANSACTION", expect("BEGIN TRANSACTION", "BEGIN", null, "BEGIN TRANSACTION")),
        Arguments.of("COMMIT", expect("COMMIT", "COMMIT", null, "COMMIT")),
        Arguments.of(
            "COMMIT TRANSACTION",
            expect("COMMIT TRANSACTION", "COMMIT", null, "COMMIT TRANSACTION")),
        Arguments.of("ROLLBACK", expect("ROLLBACK", "ROLLBACK", null, "ROLLBACK")),
        Arguments.of(
            "ROLLBACK TRANSACTION",
            expect("ROLLBACK TRANSACTION", "ROLLBACK", null, "ROLLBACK TRANSACTION")),

        // LOCK statement
        Arguments.of(
            "LOCK TABLE users", expect("LOCK TABLE users", "LOCK", "users", "LOCK TABLE users")),
        Arguments.of(
            "LOCK TABLE users IN EXCLUSIVE MODE",
            expect("LOCK TABLE users IN EXCLUSIVE MODE", "LOCK", "users", "LOCK TABLE users")),
        Arguments.of(
            "LOCK TABLES users WRITE, orders READ",
            expect("LOCK TABLES users WRITE, orders READ", "LOCK", "users", "LOCK TABLES users")),

        // USE statement
        Arguments.of("USE mydb", expect("USE mydb", "USE", null, "USE mydb")),
        Arguments.of(
            "USE `my database`", expect("USE `my database`", "USE", null, "USE `my database`")),

        // GRANT statement
        Arguments.of(
            "GRANT SELECT ON users TO some_user",
            expect("GRANT SELECT ON users TO some_user", "GRANT", null, "GRANT")),
        Arguments.of(
            "GRANT ALL PRIVILEGES ON database.* TO 'user'@'host'",
            expect("GRANT ALL PRIVILEGES ON database.* TO ?@?", "GRANT", null, "GRANT")),

        // REVOKE statement
        Arguments.of(
            "REVOKE SELECT ON users FROM some_user",
            expect("REVOKE SELECT ON users FROM some_user", "REVOKE", null, "REVOKE")),
        Arguments.of(
            "REVOKE ALL PRIVILEGES ON database.* FROM 'user'@'host'",
            expect("REVOKE ALL PRIVILEGES ON database.* FROM ?@?", "REVOKE", null, "REVOKE")),

        // SHOW statement
        Arguments.of("SHOW TABLES", expect("SHOW TABLES", "SHOW", null, "SHOW")),
        Arguments.of(
            "SHOW CREATE TABLE users", expect("SHOW CREATE TABLE users", "SHOW", null, "SHOW")),
        Arguments.of("SHOW DATABASES", expect("SHOW DATABASES", "SHOW", null, "SHOW")),

        // SQL keywords used as identifiers (table names)
        Arguments.of(
            "SELECT * FROM insert WHERE x = 1",
            expect("SELECT * FROM insert WHERE x = ?", "SELECT", "insert", "SELECT insert")),
        Arguments.of("SELECT * FROM update", expect("SELECT", "update", "SELECT update")),
        Arguments.of("SELECT * FROM delete", expect("SELECT", "delete", "SELECT delete")),
        Arguments.of("SELECT * FROM call", expect("SELECT", "call", "SELECT call")),
        Arguments.of("SELECT * FROM merge", expect("SELECT", "merge", "SELECT merge")),
        Arguments.of("SELECT * FROM create", expect("SELECT", "create", "SELECT create")),
        Arguments.of("SELECT * FROM drop", expect("SELECT", "drop", "SELECT drop")),
        Arguments.of("SELECT * FROM alter", expect("SELECT", "alter", "SELECT alter")),
        Arguments.of("SELECT * FROM exec", expect("SELECT", "exec", "SELECT exec")),
        Arguments.of("SELECT * FROM execute", expect("SELECT", "execute", "SELECT execute")),

        // Oracle database link syntax (table@dblink)
        Arguments.of(
            "SELECT * FROM users@remote_db",
            expect("SELECT", "users@remote_db", "SELECT users@remote_db")),
        Arguments.of(
            "SELECT * FROM schema.users@remote_db",
            expect("SELECT", "schema.users@remote_db", "SELECT schema.users@remote_db")),

        // SQL keywords used as column names
        Arguments.of(
            "SELECT insert, update FROM mytable", expect("SELECT", "mytable", "SELECT mytable")),

        // SQL keywords used as table aliases
        Arguments.of("SELECT * FROM mytable insert", expect("SELECT", "mytable", "SELECT mytable")),
        Arguments.of(
            "SELECT * FROM mytable AS update", expect("SELECT", "mytable", "SELECT mytable")),
        Arguments.of("SELECT * FROM user", expect("SELECT", "user", "SELECT user")),

        // CTEs (Common Table Expressions) - CTE names are filtered from query summary
        Arguments.of(
            "WITH cte AS (SELECT a FROM b) SELECT * FROM cte",
            expect("SELECT", "b", "SELECT b SELECT")),
        Arguments.of(
            "WITH cte AS (VALUES (1, 'a'), (2, 'b')) SELECT * FROM cte",
            expect(
                "WITH cte AS (VALUES (?, ?), (?, ?)) SELECT * FROM cte", "SELECT", null, "SELECT")),
        // Multiple CTEs - CTE references filtered in main query
        Arguments.of(
            "WITH a AS (SELECT * FROM t1), b AS (SELECT * FROM t2) SELECT * FROM a JOIN b ON a.id = b.id",
            expect("SELECT", "t1", "SELECT t1 SELECT t2 SELECT")),
        // Recursive CTE - self-reference filtered in CTE body and main query
        Arguments.of(
            "WITH RECURSIVE cte AS (SELECT id FROM t WHERE parent IS NULL UNION ALL SELECT t.id FROM t JOIN cte ON t.parent = cte.id) SELECT * FROM cte",
            expect("SELECT", "t", "SELECT t SELECT t SELECT")));
  }

  private static Stream<Arguments> ddlArgs() {
    return Stream.of(
        Arguments.of(
            "CREATE TABLE `table`", expect("CREATE TABLE", "`table`", "CREATE TABLE `table`")),
        Arguments.of(
            "CREATE TABLE ks.users ( id UUID PRIMARY KEY, name text )",
            expect("CREATE TABLE", "ks.users", "CREATE TABLE ks.users")),
        Arguments.of(
            "create table ks.users ( id UUID PRIMARY KEY, name text )",
            expect("create table", "ks.users", "create table ks.users")),
        Arguments.of("CREATE KEYSPACE sync_test", expect("CREATE", null, "CREATE KEYSPACE")),
        Arguments.of(
            "CREATE TABLE IF NOT EXISTS table",
            expect("CREATE TABLE", "table", "CREATE TABLE table")),
        Arguments.of("DROP TABLE `if`", expect("DROP TABLE", "`if`", "DROP TABLE `if`")),
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
            expect("CREATE VIEW", "table", "CREATE VIEW tmp SELECT table")),
        Arguments.of(
            "CREATE PROCEDURE p AS SELECT * FROM table GO",
            expect("CREATE PROCEDURE", "table", "CREATE PROCEDURE p SELECT table")),
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
                "ALTER TABLE users")),
        Arguments.of(
            "CREATE TABLE users (password VARCHAR(255))",
            expect(
                "CREATE TABLE users (password VARCHAR(?))",
                "CREATE TABLE",
                "users",
                "CREATE TABLE users")),
        Arguments.of(
            "create table users (password VARCHAR(255))",
            expect(
                "create table users (password VARCHAR(?))",
                "create table",
                "users",
                "create table users")),
        Arguments.of(
            "ALTER TABLE users ADD COLUMN password VARCHAR(255)",
            expect(
                "ALTER TABLE users ADD COLUMN password VARCHAR(?)",
                "ALTER TABLE",
                "users",
                "ALTER TABLE users")),
        Arguments.of(
            "alter table users ADD COLUMN password VARCHAR(255)",
            expect(
                "alter table users ADD COLUMN password VARCHAR(?)",
                "alter table",
                "users",
                "alter table users")),
        Arguments.of(
            "ALTER TABLE user ADD COLUMN name VARCHAR(255)",
            expect(
                "ALTER TABLE user ADD COLUMN name VARCHAR(?)",
                "ALTER TABLE",
                "user",
                "ALTER TABLE user")),
        Arguments.of(
            "CREATE TABLE password (id INT)",
            expect("CREATE TABLE", "password", "CREATE TABLE password")),
        Arguments.of(
            "DROP TABLE password", expect("DROP TABLE", "password", "DROP TABLE password")));
  }

  @ParameterizedTest
  @MethodSource("doubleQuotesAsIdentifiersArgs")
  void simplifySqlDoubleQuotesAsIdentifiers(
      String original, Function<String, SqlQuery> expectedFunction) {
    SqlQuery result = analyze(original, DOUBLE_QUOTES_ARE_IDENTIFIERS);
    SqlQuery expected = expectedFunction.apply(original);
    assertThat(result.getQueryText()).isEqualTo(expected.getQueryText());
    assertThat(result.getOperationName()).isEqualTo(expected.getOperationName());
    assertThat(result.getCollectionName()).isEqualTo(expected.getCollectionName());
    assertThat(result.getQuerySummary()).isEqualTo(expected.getQuerySummary());
  }

  private static Stream<Arguments> doubleQuotesAsIdentifiersArgs() {
    return Stream.of(
        // When dialect treats double quotes as identifiers, quoted text is preserved in query
        Arguments.of("SELECT * FROM \"TABLE\"", expect("SELECT", "\"TABLE\"", "SELECT \"TABLE\"")),
        Arguments.of(
            "SELECT x, y, z FROM \"schema table\"",
            expect("SELECT", "\"schema table\"", "SELECT \"schema table\"")),
        Arguments.of(
            "insert into \"db table\" where lalala",
            expect("insert", "\"db table\"", "insert \"db table\"")),
        Arguments.of(
            "delete from \"my table\" where something something",
            expect("delete", "\"my table\"", "delete \"my table\"")),
        Arguments.of(
            "update \"my table\" set answer=42",
            expect(
                "update \"my table\" set answer=?",
                "update",
                "\"my table\"",
                "update \"my table\"")),
        Arguments.of(
            "merge into \"my table\"", expect("merge", "\"my table\"", "merge \"my table\"")));
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(value);
    }
    return result.toString();
  }
}
