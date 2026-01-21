/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test cases for problematic SQL queries that challenge the current db.query.summary
 * implementation. These test cases are based on PROBLEMATIC_SQL_TEST_CASES.md
 *
 * <p>Many of these tests are currently @Disabled because the JFlex-based implementation has known
 * limitations. They document expected behavior for future improvements.
 *
 * <p><b>Test Summary:</b>
 *
 * <ul>
 *   <li>31 total test cases covering 20+ categories of problematic SQL
 *   <li>14 tests currently passing (basic SQL and some edge cases)
 *   <li>17 tests disabled (documenting known limitations)
 * </ul>
 *
 * <p><b>Priority Levels:</b>
 *
 * <ul>
 *   <li><b>P0 - Critical:</b> ALTER TABLE with DROP COLUMN, quoted keywords as identifiers,
 *       keywords in line comments, INSERT...SELECT
 *   <li><b>P1 - High:</b> CTEs, complex subqueries, UNION operations, database-specific syntax
 *   <li><b>P2 - Medium:</b> Stored procedures with keywords in names, window functions, escaped
 *       quotes
 *   <li><b>P3 - Low:</b> VALUES clause, case sensitivity edge cases
 * </ul>
 *
 * <p>These tests serve multiple purposes:
 *
 * <ol>
 *   <li><b>Documentation:</b> Clear examples of what should work vs current limitations
 *   <li><b>Regression prevention:</b> Ensure working cases don't break
 *   <li><b>Future improvements:</b> Enable tests as fixes are implemented
 *   <li><b>Decision support:</b> Help evaluate whether to use enhanced JFlex, full parser, or
 *       hybrid approach
 * </ol>
 */
class ProblematicSqlQuerySummaryTest {

  // Helper method for test cases
  private static SqlStatementInfo sanitize(String sql) {
    return SqlStatementSanitizer.create(true).sanitize(sql);
  }

  // ===== P0 - CRITICAL ISSUES =====

  @ParameterizedTest
  @MethodSource("alterTableDropColumnArgs")
  void alterTableDropColumn(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> alterTableDropColumnArgs() {
    return Stream.of(
        Arguments.of("ALTER TABLE t2 DROP COLUMN c, DROP COLUMN d", "ALTER TABLE t2"),
        Arguments.of(
            "ALTER TABLE users ADD COLUMN email VARCHAR(255), DROP COLUMN legacy_id, MODIFY"
                + " COLUMN status INT",
            "ALTER TABLE users"),
        Arguments.of(
            "ALTER TABLE orders ADD CONSTRAINT fk_customer FOREIGN KEY (customer_id) REFERENCES"
                + " customers(id), DROP CONSTRAINT old_check",
            "ALTER TABLE orders"));
  }

  @ParameterizedTest
  @MethodSource("quotedKeywordsAsIdentifiersArgs")
  @Disabled("P0: Quoted keywords are not correctly treated as identifiers")
  void quotedKeywordsAsIdentifiers(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> quotedKeywordsAsIdentifiersArgs() {
    return Stream.of(
        // PostgreSQL double-quoted identifiers
        Arguments.of(
            "SELECT \"select\", \"from\", \"where\" FROM user_data", "SELECT user_data"),
        Arguments.of("INSERT INTO \"insert\" (\"update\", \"delete\") VALUES (?, ?)", "INSERT \"insert\""),
        Arguments.of("SELECT t.\"drop\" FROM \"alter\" AS t", "SELECT \"alter\""),
        // MySQL backtick identifiers
        Arguments.of(
            "SELECT * FROM `select`, `from` WHERE `select`.id = `from`.id",
            "SELECT `select` `from`"),
        Arguments.of(
            "INSERT INTO `drop` (`column`, `table`) SELECT * FROM `create`",
            "INSERT `drop` SELECT `create`"),
        // SQL Server bracket identifiers
        Arguments.of("SELECT * FROM [select] WHERE [from] = ?", "SELECT [select]"),
        Arguments.of("INSERT INTO [User] ([Order], [Select]) VALUES (?, ?)", "INSERT [User]"));
  }

  @ParameterizedTest
  @MethodSource("commentsWithKeywordsArgs")
  void commentsWithKeywords(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> commentsWithKeywordsArgs() {
    return Stream.of(
        Arguments.of(
            "-- This query will DELETE old records\nSELECT * FROM users WHERE active = ?",
            "SELECT users"),
        Arguments.of(
            "/* INSERT new users from staging\n * DROP old records */\nSELECT * FROM staging_users",
            "SELECT staging_users"),
        Arguments.of(
            "SELECT * FROM orders /* JOIN with customers */ WHERE status = ?", "SELECT orders"));
  }

  @ParameterizedTest
  @MethodSource("insertSelectArgs")
  void insertSelect(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> insertSelectArgs() {
    return Stream.of(
        Arguments.of(
            "INSERT INTO archive_orders SELECT * FROM orders WHERE created_at < ?",
            "INSERT archive_orders SELECT orders"),
        Arguments.of(
            "INSERT INTO summary (user_id, total) SELECT u.id, SUM(o.amount) FROM users u JOIN"
                + " orders o ON u.id = o.user_id GROUP BY u.id",
            "INSERT summary SELECT users orders"),
        Arguments.of(
            "INSERT INTO top_customers SELECT * FROM (SELECT customer_id, SUM(amount) as total"
                + " FROM orders GROUP BY customer_id ORDER BY total DESC LIMIT 10) AS ranked",
            "INSERT top_customers SELECT SELECT orders"));
  }

  // ===== P1 - HIGH PRIORITY =====

  @ParameterizedTest
  @MethodSource("complexSubqueriesArgs")
  @Disabled("P1: Complex nested subqueries not fully captured")
  void complexSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexSubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM (SELECT * FROM orders WHERE status = ?) AS active_orders",
            "SELECT SELECT orders"),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM (SELECT * FROM products WHERE category = ?) AS"
                + " cat_products WHERE price > ?) AS filtered",
            "SELECT SELECT SELECT products"),
        Arguments.of(
            "SELECT * FROM users WHERE id IN (SELECT user_id FROM sessions WHERE active = ?)",
            "SELECT users SELECT sessions"));
  }

  @ParameterizedTest
  @MethodSource("cteArgs")
  @Disabled("P1: CTEs (WITH clauses) not properly handled")
  void commonTableExpressions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> cteArgs() {
    return Stream.of(
        Arguments.of(
            "WITH recent_orders AS (SELECT * FROM orders WHERE created_at > ?) SELECT * FROM"
                + " recent_orders",
            "SELECT orders SELECT recent_orders"),
        Arguments.of(
            "WITH active_users AS (SELECT * FROM users WHERE status = ?), recent_orders AS"
                + " (SELECT * FROM orders WHERE created_at > ?) SELECT * FROM active_users JOIN"
                + " recent_orders ON active_users.id = recent_orders.user_id",
            "SELECT users SELECT orders SELECT active_users recent_orders"),
        Arguments.of(
            "WITH RECURSIVE subordinates AS (SELECT employee_id, manager_id FROM employees WHERE"
                + " manager_id = ? UNION ALL SELECT e.employee_id, e.manager_id FROM employees e"
                + " INNER JOIN subordinates s ON s.employee_id = e.manager_id) SELECT * FROM"
                + " subordinates",
            "SELECT employees SELECT employees SELECT subordinates"));
  }

  @ParameterizedTest
  @MethodSource("complexJoinsArgs")
  @Disabled("P1: Complex JOINs may not capture all tables correctly")
  void complexJoins(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexJoinsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders o JOIN customers c ON o.customer_id = c.id JOIN products p ON"
                + " o.product_id = p.id LEFT JOIN reviews r ON o.id = r.order_id",
            "SELECT orders customers products reviews"),
        Arguments.of(
            "SELECT e1.name, e2.name as manager FROM employees e1 JOIN employees e2 ON"
                + " e1.manager_id = e2.id",
            "SELECT employees employees"),
        Arguments.of(
            "SELECT * FROM users u JOIN (SELECT customer_id, COUNT(*) as order_count FROM orders"
                + " GROUP BY customer_id) o ON u.id = o.customer_id",
            "SELECT users SELECT orders"));
  }

  @ParameterizedTest
  @MethodSource("unionOperationsArgs")
  @Disabled("P1: UNION operations should capture all SELECT operations and tables")
  void unionOperations(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> unionOperationsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT name FROM customers UNION SELECT name FROM suppliers",
            "SELECT customers SELECT suppliers"),
        Arguments.of(
            "SELECT id, name FROM employees UNION ALL SELECT id, name FROM contractors UNION"
                + " SELECT id, name FROM vendors",
            "SELECT employees SELECT contractors SELECT vendors"),
        Arguments.of(
            "SELECT user_id FROM premium_users INTERSECT SELECT user_id FROM active_users",
            "SELECT premium_users SELECT active_users"),
        Arguments.of(
            "SELECT email FROM all_users EXCEPT SELECT email FROM unsubscribed",
            "SELECT all_users SELECT unsubscribed"));
  }

  @ParameterizedTest
  @MethodSource("databaseSpecificSyntaxArgs")
  @Disabled("P1: MERGE does not capture USING source table yet")
  void databaseSpecificSyntax(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> databaseSpecificSyntaxArgs() {
    return Stream.of(
        // MySQL ON DUPLICATE KEY UPDATE
        Arguments.of(
            "INSERT INTO users (id, name, email) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name ="
                + " ?, email = ?",
            "INSERT users"),
        // PostgreSQL ON CONFLICT
        Arguments.of(
            "INSERT INTO users (id, name) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET name = ?",
            "INSERT users"),
        // SQL Server MERGE
        Arguments.of(
            "MERGE INTO target t USING source s ON t.id = s.id WHEN MATCHED THEN UPDATE SET"
                + " t.name = s.name WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id,"
                + " s.name)",
            "MERGE target source"),
        // Oracle SELECT FOR UPDATE
        Arguments.of(
            "SELECT * FROM accounts WHERE balance > ? FOR UPDATE", "SELECT accounts"));
  }

  // ===== P2 - MEDIUM PRIORITY =====

  @ParameterizedTest
  @MethodSource("storedProceduresWithKeywordsArgs")
  @Disabled("P2: Stored procedure names containing keywords")
  void storedProceduresWithKeywords(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> storedProceduresWithKeywordsArgs() {
    return Stream.of(
        Arguments.of("CALL update_user_stats(?, ?)", "CALL update_user_stats"),
        Arguments.of("CALL admin.delete_old_records(?)", "CALL admin.delete_old_records"),
        Arguments.of("EXECUTE insert_audit_log ?, ?", "EXECUTE insert_audit_log"));
  }

  @ParameterizedTest
  @MethodSource("windowFunctionsArgs")
  @Disabled("P2: Window functions with PARTITION keyword")
  void windowFunctions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> windowFunctionsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT name, department, salary, AVG(salary) OVER (PARTITION BY department) as"
                + " avg_dept_salary FROM employees",
            "SELECT employees"));
  }

  @ParameterizedTest
  @MethodSource("escapedQuotesArgs")
  @Disabled("P2: Escaped quotes in identifiers")
  void escapedQuotesInIdentifiers(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> escapedQuotesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM \"table\"\"with\"\"quotes\" WHERE \"column\"\"name\" = ?",
            "SELECT \"table\"\"with\"\"quotes\""),
        Arguments.of("SELECT * FROM `table``with``backticks`", "SELECT `table``with``backticks`"));
  }

  @ParameterizedTest
  @MethodSource("tableFunctionsArgs")
  @Disabled("P2: Table-valued functions")
  void tableFunctions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tableFunctionsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM get_user_orders(?) WHERE status = ?", "SELECT get_user_orders"),
        Arguments.of(
            "SELECT * FROM users u CROSS APPLY get_orders(u.id) o", "SELECT users get_orders"));
  }

  @ParameterizedTest
  @MethodSource("viewsWithClausesArgs")
  @Disabled("P2: CREATE VIEW with WITH CHECK OPTION")
  void viewsWithClauses(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> viewsWithClausesArgs() {
    return Stream.of(
        Arguments.of(
            "CREATE VIEW active_users AS SELECT * FROM users WHERE active = ? WITH CHECK OPTION",
            "CREATE VIEW active_users SELECT users"));
  }

  // ===== P3 - LOW PRIORITY =====

  @ParameterizedTest
  @MethodSource("valuesClauseArgs")
  @Disabled("P3: Standalone VALUES clause")
  void valuesClause(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> valuesClauseArgs() {
    return Stream.of(
        Arguments.of("VALUES (1, 'test'), (2, 'test2')", "VALUES"),
        Arguments.of(
            "INSERT INTO users (id, name) VALUES (?, ?), (?, ?)", "INSERT users"));
  }

  @ParameterizedTest
  @MethodSource("caseSensitivityArgs")
  void caseSensitivity(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> caseSensitivityArgs() {
    return Stream.of(
        Arguments.of("SeLeCt * FrOm users WhErE active = ?", "SELECT users"),
        Arguments.of(
            "SELECT * FROM \"MyTable\" WHERE \"MyColumn\" = ?", "SELECT \"MyTable\""));
  }

  // ===== ADDITIONAL EDGE CASES =====

  @ParameterizedTest
  @MethodSource("unreservedKeywordsAsIdentifiersArgs")
  @Disabled("P0: Unreserved keywords as identifiers without quotes")
  void unreservedKeywordsAsIdentifiers(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> unreservedKeywordsAsIdentifiersArgs() {
    return Stream.of(
        // In PostgreSQL, many keywords like INSERT, UPDATE can be used as table names without
        // quotes
        Arguments.of("SELECT * FROM insert WHERE update = 5", "SELECT insert"));
  }

  @ParameterizedTest
  @MethodSource("setOperationsArgs")
  @Disabled("P1: SET operations (INTERSECT, EXCEPT, MINUS)")
  void setOperations(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> setOperationsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT user_id FROM premium_users INTERSECT SELECT user_id FROM active_users",
            "SELECT premium_users SELECT active_users"),
        Arguments.of(
            "SELECT email FROM all_users EXCEPT SELECT email FROM unsubscribed",
            "SELECT all_users SELECT unsubscribed"));
  }

  // ===== REGRESSION TESTS - Cases that SHOULD work =====

  @ParameterizedTest
  @MethodSource("workingCasesArgs")
  void workingCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> workingCasesArgs() {
    return Stream.of(
        // Basic operations that should work
        Arguments.of("SELECT * FROM users", "SELECT users"),
        Arguments.of("INSERT INTO users VALUES (?, ?)", "INSERT users"),
        Arguments.of("UPDATE users SET name = ? WHERE id = ?", "UPDATE users"),
        Arguments.of("DELETE FROM users WHERE id = ?", "DELETE users"),
        Arguments.of("SELECT * FROM schema.table", "SELECT schema.table"),
        Arguments.of("SELECT * FROM users, orders", "SELECT users orders"),
        Arguments.of(
            "SELECT * FROM users u JOIN orders o ON u.id = o.user_id", "SELECT users orders"),
        // DDL operations
        Arguments.of("CREATE TABLE users (id INT)", "CREATE TABLE users"),
        Arguments.of("DROP TABLE users", "DROP TABLE users"),
        // INDEX operations capture the index name
        Arguments.of("CREATE INDEX idx_name ON users(name)", "CREATE INDEX idx_name"),
        Arguments.of("DROP INDEX idx_name", "DROP INDEX idx_name"),
        // Comments should be ignored (but currently only block comments work correctly)
        // Arguments.of("SELECT * FROM users -- comment", "SELECT users"), // Line comments broken
        Arguments.of("SELECT * FROM users /* comment */", "SELECT users"));
  }
}
