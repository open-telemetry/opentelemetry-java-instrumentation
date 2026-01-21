/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  void requireStableSemconv() {
    assumeTrue(SemconvStability.emitStableDatabaseSemconv());
  }

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
  void quotedKeywordsAsIdentifiers(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> quotedKeywordsAsIdentifiersArgs() {
    return Stream.of(
        // PostgreSQL double-quoted identifiers
        Arguments.of("SELECT \"select\", \"from\", \"where\" FROM user_data", "SELECT user_data"),
        Arguments.of(
            "INSERT INTO \"insert\" (\"update\", \"delete\") VALUES (?, ?)", "INSERT \"insert\""),
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
        // Oracle SELECT FOR UPDATE
        Arguments.of("SELECT * FROM accounts WHERE balance > ? FOR UPDATE", "SELECT accounts"),
        // SQL Server MERGE (only target table modified)
        Arguments.of(
            "MERGE INTO target t USING source s ON t.id = s.id WHEN MATCHED THEN UPDATE SET"
                + " t.name = s.name WHEN NOT MATCHED THEN INSERT (id, name) VALUES (s.id,"
                + " s.name)",
            "MERGE target"),
        // MySQL multi-table UPDATE (only target table modified)
        Arguments.of(
            "UPDATE t1 JOIN t2 ON t1.id = t2.t1_id SET t1.col = t2.col WHERE t2.status = ?",
            "UPDATE t1"),
        // MySQL multi-table DELETE (only target table modified)
        Arguments.of(
            "DELETE t1 FROM t1 JOIN t2 ON t1.id = t2.t1_id WHERE t2.status = ?", "DELETE t1"),
        // PostgreSQL UPDATE with FROM clause (only target table modified)
        Arguments.of(
            "UPDATE t1 SET col = t2.col FROM t2 WHERE t1.id = t2.t1_id AND t2.status = ?",
            "UPDATE t1"),
        // PostgreSQL DELETE with USING clause (only target table modified)
        Arguments.of(
            "DELETE FROM t1 USING t2 WHERE t1.id = t2.t1_id AND t2.status = ?", "DELETE t1"));
  }

  // ===== P2 - MEDIUM PRIORITY =====

  @ParameterizedTest
  @MethodSource("storedProceduresWithKeywordsArgs")
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
  void tableFunctions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tableFunctionsArgs() {
    return Stream.of(
        Arguments.of("SELECT * FROM get_user_orders(?) WHERE status = ?", "SELECT get_user_orders"),
        Arguments.of(
            "SELECT * FROM users u CROSS APPLY get_orders(u.id) o", "SELECT users get_orders"));
  }

  @ParameterizedTest
  @MethodSource("viewsWithClausesArgs")
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
  void valuesClause(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> valuesClauseArgs() {
    return Stream.of(
        // Standalone VALUES - PostgreSQL table value constructor
        Arguments.of("VALUES (1, 'test'), (2, 'test2')", "VALUES"),
        // INSERT with VALUES - most common use (should capture INSERT, not VALUES)
        Arguments.of("INSERT INTO users (id, name) VALUES (?, ?), (?, ?)", "INSERT users"),
        // CTE with VALUES as data source - VALUES inside CTE is not captured (CTE name is used)
        Arguments.of("WITH data AS (VALUES (1, 'a'), (2, 'b')) SELECT * FROM data", "SELECT data"),
        // UNION with VALUES - VALUES after UNION not captured (rare edge case)
        Arguments.of("SELECT name FROM users UNION VALUES ('System'), ('Admin')", "SELECT users"));
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
        Arguments.of("SELECT * FROM \"MyTable\" WHERE \"MyColumn\" = ?", "SELECT \"MyTable\""));
  }

  /**
   * Multi-statement queries documentation test.
   *
   * <p>This test documents the behavior for multi-statement queries (multiple SQL statements
   * separated by semicolons in a single string). The implementation captures a summary for each
   * statement, separated by semicolons in the output.
   *
   * <p>For example:
   *
   * <ul>
   *   <li>{@code SELECT * FROM a; SELECT * FROM b} → {@code SELECT a; SELECT b}
   *   <li>{@code INSERT INTO log VALUES (?); UPDATE counter SET val = ?} → {@code INSERT log;
   *       UPDATE counter}
   * </ul>
   */
  @ParameterizedTest
  @MethodSource("multiStatementArgs")
  void multiStatementQueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    // This test documents actual behavior - all operations are captured
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> multiStatementArgs() {
    return Stream.of(
        // Multiple SELECT statements - each statement separated by ;
        Arguments.of("SELECT * FROM a; SELECT * FROM b", "SELECT a; SELECT b"),
        // Mixed operations - each statement gets its own summary
        Arguments.of(
            "DELETE FROM temp; INSERT INTO archive SELECT * FROM temp",
            "DELETE temp; INSERT archive SELECT temp"),
        // Three statements - each operation captured with its tables
        Arguments.of(
            "INSERT INTO log VALUES (?); UPDATE counter SET val = val + ?; SELECT * FROM status",
            "INSERT log; UPDATE counter; SELECT status"));
  }

  // ===== ANSI SQL ADDITIONAL TEST CASES =====

  @ParameterizedTest
  @MethodSource("correlatedSubqueriesArgs")
  void correlatedSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> correlatedSubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders o WHERE amount > (SELECT AVG(amount) FROM orders WHERE"
                + " customer_id = o.customer_id)",
            "SELECT orders SELECT orders"),
        Arguments.of(
            "UPDATE employees e SET salary = salary * 1.1 WHERE salary < (SELECT AVG(salary) FROM"
                + " employees WHERE department_id = e.department_id)",
            "UPDATE employees SELECT employees"),
        Arguments.of(
            "DELETE FROM products p WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_id"
                + " = p.id)",
            "DELETE products SELECT order_items"));
  }

  @ParameterizedTest
  @MethodSource("scalarSubqueriesArgs")
  void scalarSubqueriesInSelectList(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> scalarSubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT name, (SELECT COUNT(*) FROM orders WHERE customer_id = c.id) as order_count"
                + " FROM customers c",
            "SELECT customers SELECT orders"),
        Arguments.of(
            "SELECT e.name, (SELECT AVG(salary) FROM employees WHERE department_id ="
                + " e.department_id) as avg_dept_salary FROM employees e",
            "SELECT employees SELECT employees"),
        Arguments.of(
            "SELECT p.name, (SELECT MAX(price) FROM products), (SELECT MIN(price) FROM products)"
                + " FROM products p",
            "SELECT products SELECT products SELECT products"));
  }

  @ParameterizedTest
  @MethodSource("existsNotExistsArgs")
  void existsAndNotExists(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> existsNotExistsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM customers WHERE EXISTS (SELECT 1 FROM orders WHERE customer_id ="
                + " customers.id)",
            "SELECT customers SELECT orders"),
        Arguments.of(
            "SELECT * FROM products WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_id"
                + " = products.id AND quantity > 0)",
            "SELECT products SELECT order_items"),
        Arguments.of(
            "SELECT * FROM users u WHERE EXISTS (SELECT 1 FROM sessions WHERE user_id = u.id) AND"
                + " NOT EXISTS (SELECT 1 FROM banned_users WHERE user_id = u.id)",
            "SELECT users SELECT sessions SELECT banned_users"));
  }

  @ParameterizedTest
  @MethodSource("allAnySomeArgs")
  void allAnySomeOperators(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> allAnySomeArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM products WHERE price > ALL (SELECT price FROM discounted_products)",
            "SELECT products SELECT discounted_products"),
        Arguments.of(
            "SELECT * FROM employees WHERE salary >= ANY (SELECT salary FROM managers)",
            "SELECT employees SELECT managers"),
        Arguments.of(
            "SELECT * FROM orders WHERE amount < SOME (SELECT amount FROM large_orders)",
            "SELECT orders SELECT large_orders"));
  }

  @ParameterizedTest
  @MethodSource("havingClauseArgs")
  void havingClauseWithSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> havingClauseArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT department_id, COUNT(*) FROM employees GROUP BY department_id HAVING COUNT(*)"
                + " > (SELECT AVG(cnt) FROM (SELECT COUNT(*) as cnt FROM employees GROUP BY"
                + " department_id) AS dept_counts)",
            "SELECT employees SELECT SELECT employees"),
        Arguments.of(
            "SELECT category, SUM(price) FROM products GROUP BY category HAVING SUM(price) >"
                + " (SELECT AVG(total) FROM category_totals)",
            "SELECT products SELECT category_totals"));
  }

  @ParameterizedTest
  @MethodSource("orderBySubqueriesArgs")
  void orderByWithSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> orderBySubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM products ORDER BY (SELECT AVG(rating) FROM reviews WHERE product_id ="
                + " products.id) DESC",
            "SELECT products SELECT reviews"),
        Arguments.of(
            "SELECT * FROM employees ORDER BY (SELECT COUNT(*) FROM projects WHERE manager_id ="
                + " employees.id)",
            "SELECT employees SELECT projects"));
  }

  @ParameterizedTest
  @MethodSource("crossJoinArgs")
  void crossJoin(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> crossJoinArgs() {
    return Stream.of(
        Arguments.of("SELECT * FROM colors CROSS JOIN sizes", "SELECT colors sizes"),
        Arguments.of(
            "SELECT * FROM t1 CROSS JOIN t2 CROSS JOIN t3", "SELECT t1 t2 t3"),
        Arguments.of(
            "SELECT * FROM products CROSS JOIN (SELECT * FROM categories WHERE active = ?) AS c",
            "SELECT products SELECT categories"));
  }

  @ParameterizedTest
  @MethodSource("naturalJoinArgs")
  void naturalJoin(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> naturalJoinArgs() {
    return Stream.of(
        Arguments.of("SELECT * FROM employees NATURAL JOIN departments", "SELECT employees departments"),
        Arguments.of(
            "SELECT * FROM t1 NATURAL LEFT JOIN t2 NATURAL RIGHT JOIN t3",
            "SELECT t1 t2 t3"));
  }

  @ParameterizedTest
  @MethodSource("fetchOffsetArgs")
  void fetchFirstAndOffset(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> fetchOffsetArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM users ORDER BY created_at OFFSET 10 ROWS FETCH FIRST 20 ROWS ONLY",
            "SELECT users"),
        Arguments.of(
            "SELECT * FROM products ORDER BY price FETCH NEXT 10 ROWS ONLY", "SELECT products"),
        Arguments.of(
            "SELECT * FROM orders OFFSET 5 ROWS FETCH NEXT 1 ROW ONLY", "SELECT orders"));
  }

  @ParameterizedTest
  @MethodSource("castExpressionArgs")
  void castExpressions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> castExpressionArgs() {
    return Stream.of(
        Arguments.of("SELECT CAST(price AS DECIMAL(10,2)) FROM products", "SELECT products"),
        Arguments.of(
            "SELECT CAST((SELECT MAX(amount) FROM orders) AS INTEGER) FROM dual",
            "SELECT dual SELECT orders"),
        Arguments.of(
            "SELECT name, CAST(created_at AS DATE) FROM users WHERE CAST(status AS INTEGER) = ?",
            "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("caseExpressionArgs")
  void caseExpressions(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> caseExpressionArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT CASE WHEN status = ? THEN 'Active' ELSE 'Inactive' END FROM users",
            "SELECT users"),
        Arguments.of(
            "SELECT CASE WHEN price > (SELECT AVG(price) FROM products) THEN 'High' ELSE 'Low'"
                + " END FROM products",
            "SELECT products SELECT products"),
        Arguments.of(
            "SELECT CASE type WHEN 'A' THEN (SELECT COUNT(*) FROM type_a) WHEN 'B' THEN (SELECT"
                + " COUNT(*) FROM type_b) ELSE 0 END FROM items",
            "SELECT items SELECT type_a SELECT type_b"));
  }

  @ParameterizedTest
  @MethodSource("deeplyNestedSubqueriesArgs")
  void deeplyNestedSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> deeplyNestedSubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM (SELECT * FROM (SELECT * FROM (SELECT * FROM users WHERE active = ?)"
                + " AS l1) AS l2) AS l3",
            "SELECT SELECT SELECT SELECT users"),
        Arguments.of(
            "SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE region_id"
                + " IN (SELECT id FROM regions WHERE country_id IN (SELECT id FROM countries WHERE"
                + " code = ?)))",
            "SELECT orders SELECT customers SELECT regions SELECT countries"));
  }

  @ParameterizedTest
  @MethodSource("inSubqueryArgs")
  void inSubquery(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> inSubqueryArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM products WHERE category_id IN (SELECT id FROM categories WHERE active"
                + " = ?)",
            "SELECT products SELECT categories"),
        Arguments.of(
            "SELECT * FROM orders WHERE (customer_id, product_id) IN (SELECT customer_id,"
                + " product_id FROM wishlists)",
            "SELECT orders SELECT wishlists"),
        Arguments.of(
            "SELECT * FROM users WHERE id NOT IN (SELECT user_id FROM banned_users)",
            "SELECT users SELECT banned_users"));
  }

  @ParameterizedTest
  @MethodSource("derivedTablesArgs")
  void derivedTablesWithComplexAliases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> derivedTablesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM (SELECT id, name FROM users) AS u (user_id, user_name)",
            "SELECT SELECT users"),
        Arguments.of(
            "SELECT * FROM (SELECT * FROM orders WHERE status = ?) AS o, (SELECT * FROM products)"
                + " AS p",
            "SELECT SELECT orders SELECT products"),
        Arguments.of(
            "SELECT derived.* FROM (SELECT a.*, b.name FROM tableA a JOIN tableB b ON a.id ="
                + " b.a_id) AS derived",
            "SELECT SELECT tableA tableB"));
  }

  @ParameterizedTest
  @MethodSource("subqueryInJoinConditionArgs")
  void subqueryInJoinCondition(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueryInJoinConditionArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders o JOIN customers c ON o.customer_id = c.id AND o.amount >"
                + " (SELECT AVG(amount) FROM orders WHERE customer_id = c.id)",
            "SELECT orders customers SELECT orders"),
        Arguments.of(
            "SELECT * FROM employees e LEFT JOIN departments d ON e.dept_id = d.id AND EXISTS"
                + " (SELECT 1 FROM projects WHERE dept_id = d.id)",
            "SELECT employees departments SELECT projects"));
  }

  @ParameterizedTest
  @MethodSource("rowValueConstructorsArgs")
  void rowValueConstructors(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> rowValueConstructorsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders WHERE (customer_id, product_id) = (?, ?)",
            "SELECT orders"),
        Arguments.of(
            "SELECT * FROM users WHERE (first_name, last_name) IN (SELECT first_name, last_name"
                + " FROM vip_users)",
            "SELECT users SELECT vip_users"));
  }

  @ParameterizedTest
  @MethodSource("whitespaceEdgeCasesArgs")
  void whitespaceEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> whitespaceEdgeCasesArgs() {
    return Stream.of(
        Arguments.of("SELECT\t*\tFROM\tusers", "SELECT users"),
        Arguments.of("SELECT\n*\nFROM\norders\nWHERE\nstatus\n=\n?", "SELECT orders"),
        Arguments.of("SELECT   *   FROM   products   WHERE   price   >   ?", "SELECT products"),
        Arguments.of("SELECT\r\n*\r\nFROM\r\ncustomers", "SELECT customers"));
  }

  @ParameterizedTest
  @MethodSource("emptyAndTrivialStatementsArgs")
  void emptyAndTrivialStatements(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> emptyAndTrivialStatementsArgs() {
    return Stream.of(
        Arguments.of(";", null),
        Arguments.of(";;", null),
        Arguments.of("   ;   ", null),
        Arguments.of("/* comment only */", null),
        Arguments.of("-- comment only", null));
  }

  @ParameterizedTest
  @MethodSource("multipleJoinTypesArgs")
  void multipleJoinTypes(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> multipleJoinTypesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders o INNER JOIN customers c ON o.customer_id = c.id LEFT JOIN"
                + " shipping s ON o.id = s.order_id RIGHT JOIN invoices i ON o.id = i.order_id"
                + " FULL OUTER JOIN payments p ON o.id = p.order_id",
            "SELECT orders customers shipping invoices payments"),
        Arguments.of(
            "SELECT * FROM t1 JOIN t2 USING (id) LEFT JOIN t3 USING (id)",
            "SELECT t1 t2 t3"));
  }

  @ParameterizedTest
  @MethodSource("setOperatorsWithParensArgs")
  void setOperatorsWithParentheses(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> setOperatorsWithParensArgs() {
    return Stream.of(
        Arguments.of(
            "(SELECT id FROM users) UNION (SELECT id FROM customers)",
            "SELECT users SELECT customers"),
        Arguments.of(
            "(SELECT * FROM t1 UNION SELECT * FROM t2) INTERSECT (SELECT * FROM t3 UNION SELECT *"
                + " FROM t4)",
            "SELECT t1 SELECT t2 SELECT t3 SELECT t4"));
  }

  @ParameterizedTest
  @MethodSource("selectIntoArgs")
  void selectInto(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> selectIntoArgs() {
    return Stream.of(
        // Note: SELECT INTO is not standard ANSI SQL but widely supported
        Arguments.of("SELECT * INTO temp_table FROM users WHERE active = ?", "SELECT users"),
        Arguments.of(
            "SELECT u.name, COUNT(o.id) INTO summary_table FROM users u LEFT JOIN orders o ON"
                + " u.id = o.user_id GROUP BY u.name",
            "SELECT users orders"));
  }

  @ParameterizedTest
  @MethodSource("complexUpdateDeleteArgs")
  void complexUpdateAndDelete(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexUpdateDeleteArgs() {
    return Stream.of(
        Arguments.of(
            "UPDATE products SET price = price * 1.1 WHERE category_id IN (SELECT id FROM"
                + " categories WHERE margin_low = ?)",
            "UPDATE products SELECT categories"),
        Arguments.of(
            "DELETE FROM orders WHERE customer_id = ? AND NOT EXISTS (SELECT 1 FROM payments"
                + " WHERE order_id = orders.id)",
            "DELETE orders SELECT payments"),
        Arguments.of(
            "UPDATE employees SET salary = (SELECT AVG(salary) FROM employees WHERE department_id"
                + " = ?) WHERE department_id = ?",
            "UPDATE employees SELECT employees"));
  }

  @ParameterizedTest
  @MethodSource("keywordSequencesArgs")
  void keywordSequences(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> keywordSequencesArgs() {
    return Stream.of(
        // Keywords that might be confused: LEFT OUTER JOIN, RIGHT OUTER JOIN, FULL OUTER JOIN
        Arguments.of(
            "SELECT * FROM t1 LEFT OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        Arguments.of(
            "SELECT * FROM t1 RIGHT OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        Arguments.of(
            "SELECT * FROM t1 FULL OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        // IS NULL, IS NOT NULL should not interfere
        Arguments.of(
            "SELECT * FROM users WHERE deleted_at IS NULL AND email IS NOT NULL",
            "SELECT users"));
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
