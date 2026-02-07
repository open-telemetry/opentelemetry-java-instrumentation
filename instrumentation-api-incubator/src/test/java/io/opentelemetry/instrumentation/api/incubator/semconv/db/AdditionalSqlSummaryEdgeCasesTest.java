/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Additional edge case tests for SQL query summary generation. These tests explore potential issues
 * with the current implementation.
 *
 * <p>KNOWN ISSUES DOCUMENTED IN THIS FILE:
 *
 * <ul>
 *   <li>TRUNCATE, REPLACE, LOCK, GRANT, REVOKE, SHOW, USE - not handled as operations
 *   <li>Trailing semicolons leave trailing ; in summary
 *   <li>Multiple empty statements (;;) produce multiple ; in summary
 *   <li>VALUES in subqueries (FROM clause) not included in summary
 *   <li>Alias named "select" incorrectly parsed as SELECT operation
 *   <li>CTE name references inside subqueries show as empty SELECT (CTE filtering issue)
 *   <li>ONLY keyword captured as table name instead of being skipped
 * </ul>
 */
class AdditionalSqlSummaryEdgeCasesTest {

  private static SqlStatementInfo sanitize(String sql) {
    return SqlStatementSanitizer.create(true).sanitizeWithSummary(sql);
  }

  // ===== UNHANDLED STATEMENT TYPES =====
  // These tests document statements that are not currently handled by the summarizer.
  // The actual values show what the parser CURRENTLY produces.
  // Comments indicate what would be ideal behavior.

  @ParameterizedTest
  @MethodSource("unhandledStatementTypesArgs")
  void unhandledStatementTypes(String sql, String actualSummary) {
    SqlStatementInfo result = sanitize(sql);
    // Document what parser ACTUALLY produces
    assertThat(result.getQuerySummary()).isEqualTo(actualSummary);
  }

  private static Stream<Arguments> unhandledStatementTypesArgs() {
    return Stream.of(
        // TRUNCATE - currently not handled (produces null)
        // Ideal: "TRUNCATE users"
        Arguments.of("TRUNCATE TABLE users", null),
        Arguments.of("TRUNCATE users", null),

        // REPLACE (MySQL) - like INSERT but not handled, VALUES keyword picks up
        // Ideal: "REPLACE users"
        Arguments.of("REPLACE INTO users VALUES (?, ?)", "VALUES"),

        // EXPLAIN/DESCRIBE - yields just the inner query (acceptable)
        Arguments.of("EXPLAIN SELECT * FROM users", "SELECT users"),

        // Transaction control - currently not handled (produces null)
        // Ideal: "BEGIN", "COMMIT", "ROLLBACK"
        Arguments.of("BEGIN", null),
        Arguments.of("COMMIT", null),
        Arguments.of("ROLLBACK", null),

        // LOCK TABLE - currently not handled
        // Ideal: "LOCK users"
        Arguments.of("LOCK TABLE users IN EXCLUSIVE MODE", null),

        // GRANT - incorrectly recognizes SELECT as operation
        // Ideal: "GRANT users" or null
        Arguments.of("GRANT SELECT ON users TO some_user", "SELECT"),

        // REVOKE - incorrectly recognizes SELECT as operation, captures FROM target
        // Ideal: "REVOKE users" or null
        Arguments.of("REVOKE SELECT ON users FROM some_user", "SELECT some_user"),

        // SHOW commands (MySQL) - incorrectly picks up CREATE TABLE
        // Ideal: "SHOW" or null
        Arguments.of("SHOW TABLES", null),
        Arguments.of("SHOW CREATE TABLE users", "CREATE TABLE users"),

        // USE database - currently not handled
        // Ideal: "USE mydb"
        Arguments.of("USE mydb", null));
  }

  // ===== TABLE FUNCTIONS AND SPECIAL FROM CLAUSE SYNTAX =====

  @ParameterizedTest
  @MethodSource("tableFunctionEdgeCasesArgs")
  void tableFunctionEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tableFunctionEdgeCasesArgs() {
    return Stream.of(
        // OUTER APPLY (SQL Server) - tests if OUTER is handled before APPLY
        Arguments.of(
            "SELECT * FROM users u OUTER APPLY get_orders(u.id) o", "SELECT users get_orders"),

        // Table function with schema prefix
        Arguments.of("SELECT * FROM dbo.fn_split(?, ',')", "SELECT dbo.fn_split"),

        // UNNEST (PostgreSQL/BigQuery)
        Arguments.of("SELECT * FROM UNNEST(ARRAY[1,2,3]) AS t", "SELECT UNNEST"),

        // GENERATE_SERIES (PostgreSQL)
        Arguments.of("SELECT * FROM GENERATE_SERIES(1, 10) AS gs", "SELECT GENERATE_SERIES"),

        // Oracle DB link syntax with @ - identifier may not include @ properly
        Arguments.of("SELECT * FROM users@remote_db", "SELECT users"),

        // KNOWN ISSUE: FROM ONLY - ONLY keyword captured as table name
        // Ideal: "SELECT parent_table"
        Arguments.of("SELECT * FROM ONLY parent_table", "SELECT ONLY"),

        // Nested function calls (not subqueries)
        Arguments.of("SELECT * FROM fn1(fn2(fn3(?)))", "SELECT fn1"),

        // Table function after CROSS JOIN
        Arguments.of("SELECT * FROM t1 CROSS JOIN UNNEST(t1.arr) AS e", "SELECT t1 UNNEST"));
  }

  // ===== SPECIAL INSERT SYNTAX =====

  @ParameterizedTest
  @MethodSource("specialInsertSyntaxArgs")
  void specialInsertSyntax(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> specialInsertSyntaxArgs() {
    return Stream.of(
        // INSERT with alias after table name (Oracle allows this)
        Arguments.of("INSERT INTO t1 a SELECT * FROM t2 WHERE t2.id = ?", "INSERT t1 SELECT t2"),

        // INSERT with column list and SELECT that has JOIN
        Arguments.of(
            "INSERT INTO archive (id, data) SELECT o.id, c.data FROM orders o JOIN customers c ON"
                + " o.cid = c.id",
            "INSERT archive SELECT orders customers"),

        // INSERT with ON CONFLICT DO NOTHING (PostgreSQL)
        Arguments.of(
            "INSERT INTO users (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING", "INSERT users"),

        // INSERT with RETURNING and subquery
        Arguments.of(
            "INSERT INTO orders (product_id) SELECT id FROM products WHERE active = ? RETURNING *",
            "INSERT orders SELECT products"));
  }

  // ===== VALUES AS TABLE SOURCE =====
  // KNOWN ISSUE: VALUES in subqueries/CTEs is not included in summary

  @ParameterizedTest
  @MethodSource("valuesAsTableSourceArgs")
  void valuesAsTableSource(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> valuesAsTableSourceArgs() {
    return Stream.of(
        // VALUES in FROM clause - not captured (currently just "SELECT")
        // Ideal: "SELECT VALUES"
        Arguments.of("SELECT * FROM (VALUES (1, 'a'), (2, 'b')) AS t(id, name)", "SELECT"),

        // VALUES joined with table - VALUES portion not captured
        // Ideal: "SELECT VALUES users"
        Arguments.of(
            "SELECT * FROM (VALUES (1), (2)) AS t(id) JOIN users u ON t.id = u.id",
            "SELECT users"),

        // CTE with VALUES body - VALUES not captured, CTE filtered
        // Ideal: "SELECT VALUES SELECT"
        Arguments.of(
            "WITH numbers AS (VALUES (1), (2), (3)) SELECT * FROM numbers", "SELECT"),

        // VALUES in UNION - VALUES not captured after UNION
        // Ideal: "SELECT users VALUES"
        Arguments.of("SELECT id FROM users UNION VALUES (1), (2)", "SELECT users"));
  }

  // ===== LATERAL AND CROSS JOIN VARIATIONS =====

  @ParameterizedTest
  @MethodSource("lateralCrossJoinVariationsArgs")
  void lateralCrossJoinVariations(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> lateralCrossJoinVariationsArgs() {
    return Stream.of(
        // CROSS JOIN LATERAL
        Arguments.of(
            "SELECT * FROM t1 CROSS JOIN LATERAL (SELECT * FROM t2 WHERE t2.id = t1.id) AS sub",
            "SELECT t1 SELECT t2"),

        // LEFT JOIN LATERAL
        Arguments.of(
            "SELECT * FROM t1 LEFT JOIN LATERAL (SELECT * FROM t2 WHERE t2.fk = t1.id LIMIT 1) sub"
                + " ON true",
            "SELECT t1 SELECT t2"),

        // Multiple LATERAL joins
        Arguments.of(
            "SELECT * FROM t1, LATERAL (SELECT * FROM t2 WHERE t2.id = t1.id) a, LATERAL (SELECT *"
                + " FROM t3 WHERE t3.id = a.id) b",
            "SELECT t1 SELECT t2 SELECT t3"));
  }

  // ===== EDGE CASES WITH ALIASES =====
  // KNOWN ISSUE: Alias named "select" is incorrectly parsed as SELECT operation

  @ParameterizedTest
  @MethodSource("aliasEdgeCasesArgs")
  void aliasEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> aliasEdgeCasesArgs() {
    return Stream.of(
        // Alias that is a keyword (without quotes) - works for "join"
        Arguments.of("SELECT * FROM users join", "SELECT users"),

        // KNOWN ISSUE: Alias named "select" parsed as SELECT operation
        // Ideal: "SELECT users"
        Arguments.of("SELECT * FROM users as select", "SELECT users SELECT"),

        // Table name same as alias of previous table
        Arguments.of(
            "SELECT * FROM orders users JOIN users u ON orders.user_id = u.id",
            "SELECT orders users"),

        // Very long alias - works fine
        Arguments.of(
            "SELECT * FROM users AS this_is_a_very_long_alias_name_that_exceeds_normal_length",
            "SELECT users"));
  }

  // ===== SPECIAL CLAUSES THAT MIGHT CONFUSE PARSER =====

  @ParameterizedTest
  @MethodSource("specialClausesArgs")
  void specialClauses(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> specialClausesArgs() {
    return Stream.of(
        // FOR UPDATE with table specification
        Arguments.of("SELECT * FROM users FOR UPDATE OF users.name NOWAIT", "SELECT users"),

        // FOR SHARE (PostgreSQL)
        Arguments.of("SELECT * FROM users FOR SHARE", "SELECT users"),

        // SKIP LOCKED (PostgreSQL 9.5+)
        Arguments.of("SELECT * FROM users FOR UPDATE SKIP LOCKED", "SELECT users"),

        // FETCH FIRST with PERCENT
        Arguments.of("SELECT * FROM users FETCH FIRST 10 PERCENT ROWS ONLY", "SELECT users"),

        // TOP with PERCENT (SQL Server)
        Arguments.of("SELECT TOP 10 PERCENT * FROM users", "SELECT users"),

        // DISTINCT ON (PostgreSQL)
        Arguments.of("SELECT DISTINCT ON (department) * FROM employees", "SELECT employees"));
  }

  // ===== MULTIPLE SEMICOLONS AND EMPTY STATEMENTS =====
  // KNOWN ISSUE: Trailing semicolons are preserved in summary

  @ParameterizedTest
  @MethodSource("multipleSemicolonsArgs")
  void multipleSemicolons(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> multipleSemicolonsArgs() {
    return Stream.of(
        // Leading semicolons - handled correctly
        Arguments.of("; SELECT * FROM users", "SELECT users"),
        Arguments.of(";; SELECT * FROM t1", "SELECT t1"),

        // KNOWN ISSUE: Trailing semicolons leave ; in summary
        // Ideal: "SELECT users"
        Arguments.of("SELECT * FROM users;;", "SELECT users;"),

        // KNOWN ISSUE: Multiple empty statements produce multiple ;
        // Ideal: "SELECT t1; SELECT t2"
        Arguments.of("; ; SELECT * FROM t1; ; SELECT * FROM t2; ;", "SELECT t1;; SELECT t2;"));
  }

  // ===== SUBQUERIES IN UNUSUAL POSITIONS =====

  @ParameterizedTest
  @MethodSource("unusualSubqueryPositionsArgs")
  void unusualSubqueryPositions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> unusualSubqueryPositionsArgs() {
    return Stream.of(
        // Subquery in LIMIT (PostgreSQL/MySQL)
        Arguments.of(
            "SELECT * FROM users LIMIT (SELECT setting FROM config WHERE key = ?)",
            "SELECT users SELECT config"),

        // Subquery in OFFSET
        Arguments.of(
            "SELECT * FROM users OFFSET (SELECT page_size FROM config) ROWS",
            "SELECT users SELECT config"),

        // Subquery as table function argument
        Arguments.of(
            "SELECT * FROM GENERATE_SERIES(1, (SELECT MAX(id) FROM users))",
            "SELECT GENERATE_SERIES SELECT users"),

        // Subquery in COALESCE
        Arguments.of(
            "SELECT COALESCE((SELECT name FROM t1 WHERE id = ?), 'default') FROM dual",
            "SELECT SELECT t1 dual"),

        // Multiple subqueries in COALESCE
        Arguments.of(
            "SELECT COALESCE((SELECT a FROM t1), (SELECT b FROM t2), (SELECT c FROM t3)) FROM dual",
            "SELECT SELECT t1 SELECT t2 SELECT t3 dual"));
  }

  // ===== COMPLEX JOIN CONDITIONS =====

  @ParameterizedTest
  @MethodSource("complexJoinConditionsArgs")
  void complexJoinConditions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexJoinConditionsArgs() {
    return Stream.of(
        // JOIN with subquery in ON clause that references outer tables
        Arguments.of(
            "SELECT * FROM orders o JOIN order_items i ON o.id = i.order_id AND i.price > (SELECT"
                + " AVG(price) FROM order_items WHERE order_id = o.id)",
            "SELECT orders order_items SELECT order_items"),

        // Multiple subqueries in join condition
        Arguments.of(
            "SELECT * FROM t1 JOIN t2 ON t1.a IN (SELECT x FROM t3) AND t2.b IN (SELECT y FROM t4)",
            "SELECT t1 t2 SELECT t3 SELECT t4"),

        // Correlated subquery referencing both join tables
        Arguments.of(
            "SELECT * FROM customers c JOIN orders o ON c.id = o.cust_id AND EXISTS (SELECT 1 FROM"
                + " vip WHERE vip.cust_id = c.id AND vip.order_id = o.id)",
            "SELECT customers orders SELECT vip"));
  }

  // ===== UPDATE AND DELETE WITH COMPLEX SUBQUERIES =====

  @ParameterizedTest
  @MethodSource("updateDeleteComplexSubqueriesArgs")
  void updateDeleteComplexSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> updateDeleteComplexSubqueriesArgs() {
    return Stream.of(
        // UPDATE with multiple SET subqueries
        Arguments.of(
            "UPDATE t1 SET a = (SELECT MAX(x) FROM t2), b = (SELECT MIN(y) FROM t3), c = (SELECT"
                + " AVG(z) FROM t4)",
            "UPDATE t1 SELECT t2 SELECT t3 SELECT t4"),

        // UPDATE with subquery in WHERE and SET
        Arguments.of(
            "UPDATE products SET price = (SELECT avg_price FROM stats) WHERE category_id IN"
                + " (SELECT id FROM categories WHERE active = ?)",
            "UPDATE products SELECT stats SELECT categories"),

        // DELETE with multiple correlated subqueries
        Arguments.of(
            "DELETE FROM orders WHERE customer_id NOT IN (SELECT id FROM customers) AND product_id"
                + " NOT IN (SELECT id FROM products)",
            "DELETE orders SELECT customers SELECT products"));
  }

  // ===== CTE (WITH) EDGE CASES =====

  @ParameterizedTest
  @MethodSource("cteAdvancedEdgeCasesArgs")
  void cteAdvancedEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> cteAdvancedEdgeCasesArgs() {
    return Stream.of(
        // CTE with INSERT
        Arguments.of(
            "WITH new_data AS (SELECT * FROM staging WHERE processed = ?) INSERT INTO archive"
                + " SELECT * FROM new_data",
            "SELECT staging INSERT archive SELECT"),

        // CTE with UPDATE - KNOWN ISSUE: CTE "updates" referenced in subqueries shows empty SELECT
        // The CTE name is filtered (correctly) but inside subqueries it appears as just "SELECT"
        // Ideal: "SELECT temp UPDATE target SELECT updates SELECT updates"
        Arguments.of(
            "WITH updates AS (SELECT id, new_value FROM temp) UPDATE target SET value = (SELECT"
                + " new_value FROM updates WHERE updates.id = target.id) WHERE EXISTS (SELECT 1"
                + " FROM updates WHERE updates.id = target.id)",
            "SELECT temp UPDATE target SELECT SELECT"),

        // CTE with DELETE
        Arguments.of(
            "WITH to_delete AS (SELECT id FROM old_records WHERE date < ?) DELETE FROM records"
                + " WHERE id IN (SELECT id FROM to_delete)",
            "SELECT old_records DELETE records SELECT"),

        // Nested CTE references - all CTE names filtered, shows empty SELECTs
        Arguments.of(
            "WITH a AS (SELECT 1 as x), b AS (SELECT x FROM a), c AS (SELECT x FROM b) SELECT *"
                + " FROM c",
            "SELECT SELECT SELECT SELECT"),

        // CTE with same name as real table (CTE shadows table)
        Arguments.of(
            "WITH users AS (SELECT * FROM active_users) SELECT * FROM users",
            "SELECT active_users SELECT"));
  }

  // ===== QUOTED IDENTIFIERS WITH SPECIAL CHARACTERS =====

  @ParameterizedTest
  @MethodSource("quotedIdentifiersSpecialCharsArgs")
  void quotedIdentifiersSpecialChars(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> quotedIdentifiersSpecialCharsArgs() {
    return Stream.of(
        // Double-quoted identifier with spaces
        Arguments.of("SELECT * FROM \"my table\"", "SELECT \"my table\""),

        // Backtick identifier with spaces (MySQL)
        Arguments.of("SELECT * FROM `my table`", "SELECT `my table`"),

        // Bracket identifier with spaces (SQL Server)
        Arguments.of("SELECT * FROM [my table]", "SELECT [my table]"),

        // Quoted identifier with dots
        Arguments.of("SELECT * FROM \"table.name\"", "SELECT \"table.name\""),

        // Mixed quoting styles
        Arguments.of(
            "SELECT * FROM \"schema\".`table` JOIN [other] ON \"schema\".`table`.id = [other].id",
            "SELECT \"schema\".`table` [other]"));
  }

  // ===== WINDOW FUNCTIONS THAT MIGHT BE CONFUSED WITH TABLES =====

  @ParameterizedTest
  @MethodSource("windowFunctionsConfusionArgs")
  void windowFunctionsNotConfusedWithTables(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> windowFunctionsConfusionArgs() {
    return Stream.of(
        // PARTITION BY shouldn't capture partition fields as tables
        Arguments.of(
            "SELECT SUM(amount) OVER (PARTITION BY category, region ORDER BY date) FROM sales",
            "SELECT sales"),

        // ROWS/RANGE BETWEEN shouldn't cause issues
        Arguments.of(
            "SELECT SUM(val) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)"
                + " FROM data",
            "SELECT data"),

        // Multiple window functions
        Arguments.of(
            "SELECT ROW_NUMBER() OVER (ORDER BY a), RANK() OVER (PARTITION BY b ORDER BY c) FROM"
                + " tbl",
            "SELECT tbl"),

        // Window function with subquery in PARTITION BY
        Arguments.of(
            "SELECT SUM(amount) OVER (PARTITION BY (SELECT type FROM types WHERE types.id ="
                + " sales.type_id)) FROM sales",
            "SELECT SELECT types sales"));
  }
}
