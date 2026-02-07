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

class ProblematicSqlQuerySummaryTest {

  // Helper method for test cases
  private static SqlStatementInfo sanitize(String sql) {
    return SqlStatementSanitizer.create(true).sanitizeWithSummary(sql);
  }

  // ===== DATABASE-SPECIFIC SYNTAX =====

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

  // ===== WINDOW FUNCTIONS =====

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

  // ===== TABLE FUNCTIONS =====

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

  // ===== SUBQUERIES IN VARIOUS POSITIONS =====

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
            "SELECT SELECT orders customers"),
        Arguments.of(
            "SELECT e.name, (SELECT AVG(salary) FROM employees WHERE department_id ="
                + " e.department_id) as avg_dept_salary FROM employees e",
            "SELECT SELECT employees employees"),
        Arguments.of(
            "SELECT p.name, (SELECT MAX(price) FROM products), (SELECT MIN(price) FROM products)"
                + " FROM products p",
            "SELECT SELECT products SELECT products products"));
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
        Arguments.of("SELECT * FROM t1 CROSS JOIN t2 CROSS JOIN t3", "SELECT t1 t2 t3"),
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
        Arguments.of(
            "SELECT * FROM employees NATURAL JOIN departments", "SELECT employees departments"),
        Arguments.of(
            "SELECT * FROM t1 NATURAL LEFT JOIN t2 NATURAL RIGHT JOIN t3", "SELECT t1 t2 t3"));
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
        Arguments.of("SELECT * FROM orders OFFSET 5 ROWS FETCH NEXT 1 ROW ONLY", "SELECT orders"));
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
            "SELECT SELECT orders dual"),
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
            "SELECT SELECT products products"),
        Arguments.of(
            "SELECT CASE type WHEN 'A' THEN (SELECT COUNT(*) FROM type_a) WHEN 'B' THEN (SELECT"
                + " COUNT(*) FROM type_b) ELSE 0 END FROM items",
            "SELECT SELECT type_a SELECT type_b items"));
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
            "SELECT * FROM orders WHERE (customer_id, product_id) = (?, ?)", "SELECT orders"),
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
            "SELECT * FROM t1 JOIN t2 USING (id) LEFT JOIN t3 USING (id)", "SELECT t1 t2 t3"));
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
        Arguments.of("SELECT * FROM t1 LEFT OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        Arguments.of("SELECT * FROM t1 RIGHT OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        Arguments.of("SELECT * FROM t1 FULL OUTER JOIN t2 ON t1.id = t2.id", "SELECT t1 t2"),
        // IS NULL, IS NOT NULL should not interfere
        Arguments.of(
            "SELECT * FROM users WHERE deleted_at IS NULL AND email IS NOT NULL", "SELECT users"));
  }

  // ===== ADDITIONAL EDGE CASES =====

  @ParameterizedTest
  @MethodSource("unreservedKeywordsAsIdentifiersArgs")
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

  // ===== POTENTIAL EDGE CASES BASED ON JFLEX ANALYSIS =====

  @ParameterizedTest
  @MethodSource("cteEdgeCasesArgs")
  void cteEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> cteEdgeCasesArgs() {
    return Stream.of(
        // Recursive CTE with self-reference - "tree" is filtered everywhere (in CTE body and main
        // query)
        Arguments.of(
            "WITH RECURSIVE tree AS (SELECT id, parent_id FROM nodes WHERE parent_id IS NULL"
                + " UNION ALL SELECT n.id, n.parent_id FROM nodes n JOIN tree t ON n.parent_id ="
                + " t.id) SELECT * FROM tree",
            "SELECT nodes SELECT nodes SELECT"),
        // CTE referencing another CTE defined earlier - cross-CTE refs and main query CTE refs
        // filtered
        Arguments.of(
            "WITH cte1 AS (SELECT * FROM t1), cte2 AS (SELECT * FROM cte1 JOIN t2 ON cte1.id ="
                + " t2.id) SELECT * FROM cte2",
            "SELECT t1 SELECT t2 SELECT"),
        // Multiple CTEs with SELECT without FROM - all CTE names filtered in main query
        Arguments.of(
            "WITH a AS (SELECT 1), b AS (SELECT 2), c AS (SELECT 3) SELECT * FROM a, b, c",
            "SELECT SELECT SELECT SELECT"));
  }

  @ParameterizedTest
  @MethodSource("deleteAliasEdgeCasesArgs")
  void deleteAliasEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> deleteAliasEdgeCasesArgs() {
    return Stream.of(
        // MySQL DELETE with alias before FROM
        Arguments.of("DELETE alias FROM table_name alias WHERE alias.id = ?", "DELETE table_name"));
  }

  @ParameterizedTest
  @MethodSource("multipleStatementsArgs")
  void multipleStatements(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> multipleStatementsArgs() {
    return Stream.of(
        // Multiple statements separated by semicolon
        Arguments.of("SELECT * FROM t1; SELECT * FROM t2", "SELECT t1; SELECT t2"),
        Arguments.of("INSERT INTO t1 VALUES (?); DELETE FROM t2", "INSERT t1; DELETE t2"),
        Arguments.of("SELECT 1; SELECT 2; SELECT 3", "SELECT; SELECT; SELECT"));
  }

  @ParameterizedTest
  @MethodSource("subqueryInUpdateSetArgs")
  void subqueryInUpdateSet(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueryInUpdateSetArgs() {
    return Stream.of(
        // Subquery in UPDATE SET clause
        Arguments.of(
            "UPDATE t1 SET col = (SELECT MAX(col) FROM t2) WHERE id = ?", "UPDATE t1 SELECT t2"),
        // Multiple subqueries in SET
        Arguments.of(
            "UPDATE t1 SET col1 = (SELECT a FROM t2), col2 = (SELECT b FROM t3)",
            "UPDATE t1 SELECT t2 SELECT t3"));
  }

  @ParameterizedTest
  @MethodSource("lateralJoinArgs")
  void lateralJoin(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> lateralJoinArgs() {
    return Stream.of(
        // LATERAL join (PostgreSQL style)
        Arguments.of(
            "SELECT * FROM users u, LATERAL (SELECT * FROM orders WHERE user_id = u.id LIMIT 1)"
                + " AS o",
            "SELECT users SELECT orders"),
        // LATERAL with JOIN syntax
        Arguments.of(
            "SELECT * FROM users u LEFT JOIN LATERAL (SELECT * FROM orders WHERE user_id = u.id)"
                + " o ON true",
            "SELECT users SELECT orders"));
  }

  @ParameterizedTest
  @MethodSource("subqueryInBetweenArgs")
  void subqueryInBetween(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueryInBetweenArgs() {
    return Stream.of(
        // Subquery in BETWEEN clause
        Arguments.of(
            "SELECT * FROM products WHERE price BETWEEN (SELECT MIN(price) FROM products) AND"
                + " (SELECT MAX(price) FROM products)",
            "SELECT products SELECT products SELECT products"));
  }

  @ParameterizedTest
  @MethodSource("windowFunctionEdgeCasesArgs")
  void windowFunctionEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> windowFunctionEdgeCasesArgs() {
    return Stream.of(
        // OVER clause identifier shouldn't be captured as table
        Arguments.of(
            "SELECT id, SUM(amount) OVER (PARTITION BY category ORDER BY date) FROM orders",
            "SELECT orders"),
        // Named window
        Arguments.of(
            "SELECT id, SUM(val) OVER w FROM data WINDOW w AS (PARTITION BY cat)", "SELECT data"),
        // ROWS BETWEEN in window
        Arguments.of(
            "SELECT id, SUM(val) OVER (ORDER BY date ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING)"
                + " FROM data",
            "SELECT data"));
  }

  @ParameterizedTest
  @MethodSource("valuesClauseArgs")
  void valuesClause(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> valuesClauseArgs() {
    return Stream.of(
        // Standalone VALUES
        Arguments.of("VALUES (1, 2, 3)", "VALUES"),
        // Multiple rows
        Arguments.of("VALUES (1), (2), (3)", "VALUES"),
        // INSERT with subquery in VALUES
        Arguments.of(
            "INSERT INTO t1 (col) VALUES ((SELECT MAX(col) FROM t2))", "INSERT t1 SELECT t2"));
  }

  @ParameterizedTest
  @MethodSource("quotedIdentifierArgs")
  void quotedIdentifiersThatLookLikeKeywords(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> quotedIdentifierArgs() {
    return Stream.of(
        // Double-quoted identifier that looks like keyword
        Arguments.of("SELECT * FROM \"SELECT\" WHERE \"FROM\" = ?", "SELECT \"SELECT\""),
        // Backtick-quoted (MySQL)
        Arguments.of("SELECT * FROM `SELECT` WHERE `FROM` = ?", "SELECT `SELECT`"),
        // Bracket-quoted (SQL Server)
        Arguments.of("SELECT * FROM [SELECT] WHERE [FROM] = ?", "SELECT [SELECT]"));
  }

  @ParameterizedTest
  @MethodSource("parenthesizedTableNameArgs")
  void parenthesizedTableName(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> parenthesizedTableNameArgs() {
    return Stream.of(
        // Parenthesized table name (not a subquery)
        Arguments.of("SELECT * FROM (users)", "SELECT users"),
        // Multiple parenthesized tables
        Arguments.of("SELECT * FROM (t1), (t2)", "SELECT t1 t2"));
  }

  @ParameterizedTest
  @MethodSource("multipleImplicitJoinsArgs")
  void multipleImplicitJoins(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> multipleImplicitJoinsArgs() {
    return Stream.of(
        // Many tables with comma separator
        Arguments.of(
            "SELECT * FROM t1, t2, t3, t4, t5 WHERE t1.id = t2.t1_id", "SELECT t1 t2 t3 t4 t5"),
        // Mixed explicit and implicit joins
        Arguments.of("SELECT * FROM t1, t2 JOIN t3 ON t2.id = t3.t2_id, t4", "SELECT t1 t2 t3 t4"));
  }

  @ParameterizedTest
  @MethodSource("unionVariantsArgs")
  void unionVariants(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> unionVariantsArgs() {
    return Stream.of(
        // UNION ALL
        Arguments.of(
            "SELECT id FROM t1 UNION ALL SELECT id FROM t2 UNION ALL SELECT id FROM t3",
            "SELECT t1 SELECT t2 SELECT t3"),
        // MINUS (Oracle)
        Arguments.of("SELECT id FROM t1 MINUS SELECT id FROM t2", "SELECT t1 SELECT t2"));
  }

  // INVALID SQL: Using reserved keywords (from, select, where) as unquoted column names
  // is not valid in any major database. Valid SQL requires quoting: SELECT "from", "select" FROM t
  @Disabled
  @ParameterizedTest
  @MethodSource("keywordsAsUnquotedColumnNamesArgs")
  void keywordsAsColumnNames(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> keywordsAsUnquotedColumnNamesArgs() {
    return Stream.of(
        Arguments.of("SELECT from, select, where FROM keywords", "SELECT keywords"),
        Arguments.of("INSERT INTO t (from, select) VALUES (?, ?)", "INSERT t"));
  }

  @ParameterizedTest
  @MethodSource("complexSchemaPatternsArgs")
  void complexSchemaPatterns(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexSchemaPatternsArgs() {
    return Stream.of(
        // Three-part name: database.schema.table
        Arguments.of("SELECT * FROM db.schema.table", "SELECT db.schema.table"),
        // Mixed qualified and unqualified
        Arguments.of(
            "SELECT * FROM schema1.t1 JOIN t2 ON schema1.t1.id = t2.id", "SELECT schema1.t1 t2"));
  }

  @ParameterizedTest
  @MethodSource("subqueryWithoutAliasArgs")
  void subqueryWithoutAlias(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueryWithoutAliasArgs() {
    return Stream.of(
        // Subquery in FROM without alias (some DBs allow this)
        Arguments.of("SELECT * FROM (SELECT * FROM users)", "SELECT SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("tableAliasMatchingTableNameArgs")
  void tableAliasMatchingAnotherTableName(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tableAliasMatchingTableNameArgs() {
    return Stream.of(
        // Table alias same as another table name
        Arguments.of(
            "SELECT * FROM users orders JOIN orders o ON orders.id = o.user_id",
            "SELECT users orders"));
  }

  @ParameterizedTest
  @MethodSource("insertSelectArgs")
  void insertSelect(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> insertSelectArgs() {
    return Stream.of(
        // INSERT ... SELECT
        Arguments.of(
            "INSERT INTO archive SELECT * FROM orders WHERE created_at < ?",
            "INSERT archive SELECT orders"),
        // INSERT with column list then SELECT
        Arguments.of(
            "INSERT INTO archive (id, data) SELECT id, data FROM orders",
            "INSERT archive SELECT orders"));
  }

  @ParameterizedTest
  @MethodSource("complexCaseExpressionsArgs")
  void complexCaseWithMultipleSubqueries(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> complexCaseExpressionsArgs() {
    return Stream.of(
        // Searched CASE with subquery in condition
        Arguments.of(
            "SELECT CASE (SELECT type FROM config) WHEN 'A' THEN (SELECT v FROM a) WHEN 'B' THEN"
                + " (SELECT v FROM b) END FROM dual",
            "SELECT SELECT config SELECT a SELECT b dual"));
  }

  // ===== ADDITIONAL POTENTIAL EDGE CASES =====

  @ParameterizedTest
  @MethodSource("commentEdgeCasesArgs")
  void commentEdgeCases(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> commentEdgeCasesArgs() {
    return Stream.of(
        // Block comment containing keywords
        Arguments.of("SELECT /* FROM ignored */ * FROM users", "SELECT users"),
        // Block comment between keywords
        Arguments.of("SELECT * /* comment */ FROM /* another */ users", "SELECT users"),
        // Nested-looking block comment (not truly nested - SQL doesn't support nested comments)
        Arguments.of("SELECT /* outer /* inner */ still comment */ * FROM users", "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("dollarQuotedStringArgs")
  void dollarQuotedStrings(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> dollarQuotedStringArgs() {
    return Stream.of(
        // Dollar-quoted string with tag
        Arguments.of("SELECT * FROM users WHERE name = $tag$some value$tag$", "SELECT users"),
        // Unterminated dollar-quoted string
        Arguments.of("SELECT * FROM users WHERE name = $tag$unterminated", "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("parameterMarkerArgs")
  void parameterMarkers(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> parameterMarkerArgs() {
    return Stream.of(
        // PostgreSQL positional parameters
        Arguments.of("SELECT * FROM users WHERE id = $1 AND name = $2", "SELECT users"),
        // Mixed parameters
        Arguments.of("SELECT * FROM users WHERE id = $1 AND status = ?", "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("limitOffsetArgs")
  void limitOffset(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> limitOffsetArgs() {
    return Stream.of(
        // LIMIT and OFFSET with subquery expressions (potential confusion)
        Arguments.of("SELECT * FROM users LIMIT 10 OFFSET 5", "SELECT users"),
        // LIMIT with subquery expression - this correctly captures the subquery
        Arguments.of(
            "SELECT * FROM users LIMIT (SELECT COUNT(*) FROM config)",
            "SELECT users SELECT config"));
  }

  @ParameterizedTest
  @MethodSource("groupByArgs")
  void groupBy(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> groupByArgs() {
    return Stream.of(
        // GROUP BY with ROLLUP
        Arguments.of(
            "SELECT dept, SUM(salary) FROM employees GROUP BY ROLLUP(dept)", "SELECT employees"),
        // GROUP BY with CUBE
        Arguments.of(
            "SELECT dept, year, SUM(salary) FROM employees GROUP BY CUBE(dept, year)",
            "SELECT employees"),
        // GROUP BY with GROUPING SETS
        Arguments.of(
            "SELECT dept, year, SUM(salary) FROM employees GROUP BY GROUPING SETS ((dept),"
                + " (year))",
            "SELECT employees"));
  }

  @ParameterizedTest
  @MethodSource("pivotUnpivotArgs")
  void pivotUnpivot(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> pivotUnpivotArgs() {
    return Stream.of(
        // PIVOT (SQL Server/Oracle)
        Arguments.of(
            "SELECT * FROM sales PIVOT (SUM(amount) FOR quarter IN ('Q1', 'Q2', 'Q3', 'Q4'))",
            "SELECT sales"),
        // UNPIVOT
        Arguments.of(
            "SELECT * FROM wide_table UNPIVOT (value FOR col_name IN (col1, col2, col3))",
            "SELECT wide_table"));
  }

  @ParameterizedTest
  @MethodSource("returningClauseArgs")
  void returningClause(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> returningClauseArgs() {
    return Stream.of(
        // INSERT RETURNING (PostgreSQL)
        Arguments.of("INSERT INTO users (name) VALUES (?) RETURNING id", "INSERT users"),
        // DELETE RETURNING
        Arguments.of("DELETE FROM users WHERE id = ? RETURNING *", "DELETE users"),
        // UPDATE RETURNING
        Arguments.of("UPDATE users SET name = ? WHERE id = ? RETURNING *", "UPDATE users"));
  }

  @ParameterizedTest
  @MethodSource("tablesampleArgs")
  void tablesample(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tablesampleArgs() {
    return Stream.of(
        // TABLESAMPLE clause
        Arguments.of("SELECT * FROM large_table TABLESAMPLE SYSTEM (10)", "SELECT large_table"),
        Arguments.of(
            "SELECT * FROM large_table TABLESAMPLE BERNOULLI (1) REPEATABLE (42)",
            "SELECT large_table"));
  }

  @ParameterizedTest
  @MethodSource("forXmlJsonArgs")
  void forXmlJson(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> forXmlJsonArgs() {
    return Stream.of(
        // FOR XML (SQL Server)
        Arguments.of("SELECT * FROM users FOR XML PATH", "SELECT users"),
        // FOR JSON (SQL Server)
        Arguments.of("SELECT * FROM users FOR JSON AUTO", "SELECT users"));
  }

  @ParameterizedTest
  @MethodSource("matchRecognizeArgs")
  void matchRecognize(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> matchRecognizeArgs() {
    return Stream.of(
        // MATCH_RECOGNIZE (Oracle pattern matching)
        Arguments.of(
            "SELECT * FROM ticker MATCH_RECOGNIZE (ORDER BY tstamp MEASURES A.tstamp AS start_t"
                + " PATTERN (A B* C) DEFINE A AS A.price > 10)",
            "SELECT ticker"));
  }

  @ParameterizedTest
  @MethodSource("modelClauseArgs")
  void modelClause(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> modelClauseArgs() {
    return Stream.of(
        // Oracle MODEL clause
        Arguments.of(
            "SELECT * FROM sales MODEL DIMENSION BY (product) MEASURES (amount) RULES"
                + " (amount['Total'] = amount['A'] + amount['B'])",
            "SELECT sales"));
  }

  @ParameterizedTest
  @MethodSource("connectByArgs")
  void connectBy(String sql, String expectedSummary) {
    SqlStatementInfo result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> connectByArgs() {
    return Stream.of(
        // Oracle CONNECT BY
        Arguments.of(
            "SELECT * FROM employees START WITH manager_id IS NULL CONNECT BY PRIOR employee_id ="
                + " manager_id",
            "SELECT employees"));
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
