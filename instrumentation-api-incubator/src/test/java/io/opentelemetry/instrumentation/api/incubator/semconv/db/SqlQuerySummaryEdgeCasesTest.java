/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AI generated edge case tests for SQL query summary generation. Covers database-specific syntax,
 * subqueries, CTEs, window functions, vendor-specific features, and various lexical edge cases.
 */
class SqlQuerySummaryEdgeCasesTest {

  private static final SqlQueryAnalyzer ANALYZER = SqlQueryAnalyzer.create(true);

  private static SqlQuery sanitize(String sql) {
    return ANALYZER.analyzeWithSummary(sql, DOUBLE_QUOTES_ARE_STRING_LITERALS);
  }

  // ===== DATABASE-SPECIFIC DML SYNTAX =====

  @ParameterizedTest
  @MethodSource("databaseSpecificDmlArgs")
  void databaseSpecificDml(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> databaseSpecificDmlArgs() {
    return Stream.of(
        // MySQL multi-table UPDATE
        Arguments.of(
            "UPDATE t1 JOIN t2 ON t1.id = t2.t1_id SET t1.col = t2.col WHERE t2.status = ?",
            "UPDATE t1"),
        // MySQL multi-table DELETE
        Arguments.of(
            "DELETE t1 FROM t1 JOIN t2 ON t1.id = t2.t1_id WHERE t2.status = ?", "DELETE t1"),
        // PostgreSQL UPDATE with FROM clause
        Arguments.of(
            "UPDATE t1 SET col = t2.col FROM t2 WHERE t1.id = t2.t1_id AND t2.status = ?",
            "UPDATE t1"),
        // PostgreSQL DELETE with USING clause
        Arguments.of(
            "DELETE FROM t1 USING t2 WHERE t1.id = t2.t1_id AND t2.status = ?", "DELETE t1"));
  }

  // ===== INSERT VARIATIONS =====

  @ParameterizedTest
  @MethodSource("insertVariationsArgs")
  void insertVariations(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> insertVariationsArgs() {
    return Stream.of(
        // INSERT with alias after table name (Oracle)
        Arguments.of("INSERT INTO t1 a SELECT * FROM t2 WHERE t2.id = ?", "INSERT t1 SELECT t2"),
        // INSERT with column list and SELECT that has JOIN
        Arguments.of(
            "INSERT INTO archive (id, data) SELECT o.id, c.data FROM orders o JOIN customers c ON"
                + " o.cid = c.id",
            "INSERT archive SELECT orders customers"),
        // INSERT with RETURNING and subquery
        Arguments.of(
            "INSERT INTO orders (product_id) SELECT id FROM products WHERE active = ? RETURNING *",
            "INSERT orders SELECT products"));
  }

  // ===== UPDATE AND DELETE WITH SUBQUERIES =====

  @ParameterizedTest
  @MethodSource("updateDeleteWithSubqueriesArgs")
  void updateDeleteWithSubqueries(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> updateDeleteWithSubqueriesArgs() {
    return Stream.of(
        // UPDATE with subquery in WHERE
        Arguments.of(
            "UPDATE products SET price = price * 1.1 WHERE category_id IN (SELECT id FROM"
                + " categories WHERE margin_low = ?)",
            "UPDATE products SELECT categories"),
        // UPDATE with self-referencing subquery in SET
        Arguments.of(
            "UPDATE employees SET salary = (SELECT AVG(salary) FROM employees WHERE department_id"
                + " = ?) WHERE department_id = ?",
            "UPDATE employees SELECT employees"),
        // UPDATE with subquery in both SET and WHERE
        Arguments.of(
            "UPDATE products SET price = (SELECT avg_price FROM stats) WHERE category_id IN"
                + " (SELECT id FROM categories WHERE active = ?)",
            "UPDATE products SELECT stats SELECT categories"),
        // UPDATE with multiple SET subqueries
        Arguments.of(
            "UPDATE t1 SET a = (SELECT MAX(x) FROM t2), b = (SELECT MIN(y) FROM t3), c = (SELECT"
                + " AVG(z) FROM t4)",
            "UPDATE t1 SELECT t2 SELECT t3 SELECT t4"),
        // DELETE with multiple NOT IN subqueries
        Arguments.of(
            "DELETE FROM orders WHERE customer_id NOT IN (SELECT id FROM customers) AND product_id"
                + " NOT IN (SELECT id FROM products)",
            "DELETE orders SELECT customers SELECT products"));
  }

  // ===== JOINS =====

  @ParameterizedTest
  @MethodSource("joinsArgs")
  void joins(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> joinsArgs() {
    return Stream.of(
        // CROSS JOIN
        Arguments.of("SELECT * FROM colors CROSS JOIN sizes", "SELECT colors sizes"),
        Arguments.of("SELECT * FROM t1 CROSS JOIN t2 CROSS JOIN t3", "SELECT t1 t2 t3"),
        Arguments.of(
            "SELECT * FROM products CROSS JOIN (SELECT * FROM categories WHERE active = ?) AS c",
            "SELECT products SELECT categories"),
        // NATURAL JOIN
        Arguments.of(
            "SELECT * FROM employees NATURAL JOIN departments", "SELECT employees departments"),
        Arguments.of(
            "SELECT * FROM t1 NATURAL LEFT JOIN t2 NATURAL RIGHT JOIN t3", "SELECT t1 t2 t3"),
        // Multiple join types in one query
        Arguments.of(
            "SELECT * FROM orders o INNER JOIN customers c ON o.customer_id = c.id LEFT JOIN"
                + " shipping s ON o.id = s.order_id RIGHT JOIN invoices i ON o.id = i.order_id"
                + " FULL OUTER JOIN payments p ON o.id = p.order_id",
            "SELECT orders customers shipping invoices payments"),
        // JOIN with USING
        Arguments.of(
            "SELECT * FROM t1 JOIN t2 USING (id) LEFT JOIN t3 USING (id)", "SELECT t1 t2 t3"),
        // IS NULL / IS NOT NULL should not interfere
        Arguments.of(
            "SELECT * FROM users WHERE deleted_at IS NULL AND email IS NOT NULL", "SELECT users"));
  }

  // ===== LATERAL JOINS =====

  @ParameterizedTest
  @MethodSource("lateralJoinsArgs")
  void lateralJoins(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> lateralJoinsArgs() {
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

  // ===== TABLE FUNCTIONS =====

  @ParameterizedTest
  @MethodSource("tableFunctionsArgs")
  void tableFunctions(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> tableFunctionsArgs() {
    return Stream.of(
        Arguments.of("SELECT * FROM get_user_orders(?) WHERE status = ?", "SELECT get_user_orders"),
        // Schema-prefixed table function
        Arguments.of("SELECT * FROM dbo.fn_split(?, ',')", "SELECT dbo.fn_split"),
        // UNNEST (PostgreSQL/BigQuery)
        Arguments.of("SELECT * FROM UNNEST(ARRAY[1,2,3]) AS t", "SELECT UNNEST"),
        // GENERATE_SERIES (PostgreSQL)
        Arguments.of("SELECT * FROM GENERATE_SERIES(1, 10) AS gs", "SELECT GENERATE_SERIES"),
        // Nested function calls
        Arguments.of("SELECT * FROM fn1(fn2(fn3(?)))", "SELECT fn1"),
        // Table function after CROSS JOIN
        Arguments.of("SELECT * FROM t1 CROSS JOIN UNNEST(t1.arr) AS e", "SELECT t1 UNNEST"));
  }

  // ===== SCALAR SUBQUERIES IN SELECT LIST =====

  @ParameterizedTest
  @MethodSource("scalarSubqueriesArgs")
  void scalarSubqueries(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> scalarSubqueriesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT e.name, (SELECT AVG(salary) FROM employees WHERE department_id ="
                + " e.department_id) as avg_dept_salary FROM employees e",
            "SELECT SELECT employees employees"),
        Arguments.of(
            "SELECT p.name, (SELECT MAX(price) FROM products), (SELECT MIN(price) FROM products)"
                + " FROM products p",
            "SELECT SELECT products SELECT products products"));
  }

  // ===== EXISTS AND NOT EXISTS =====

  @ParameterizedTest
  @MethodSource("existsNotExistsArgs")
  void existsAndNotExists(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
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

  // ===== ALL / ANY / SOME OPERATORS =====

  @ParameterizedTest
  @MethodSource("allAnySomeArgs")
  void allAnySomeOperators(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
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

  // ===== IN / NOT IN SUBQUERIES =====

  @ParameterizedTest
  @MethodSource("inSubqueryArgs")
  void inSubqueries(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> inSubqueryArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders WHERE (customer_id, product_id) IN (SELECT customer_id,"
                + " product_id FROM wishlists)",
            "SELECT orders SELECT wishlists"),
        Arguments.of(
            "SELECT * FROM users WHERE id NOT IN (SELECT user_id FROM banned_users)",
            "SELECT users SELECT banned_users"));
  }

  // ===== DERIVED TABLES =====

  @ParameterizedTest
  @MethodSource("derivedTablesArgs")
  void derivedTables(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> derivedTablesArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM (SELECT id, name FROM users) AS u (user_id, user_name)",
            "SELECT SELECT users"));
  }

  // ===== DEEPLY NESTED SUBQUERIES =====

  @ParameterizedTest
  @MethodSource("deeplyNestedSubqueriesArgs")
  void deeplyNestedSubqueries(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
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

  // ===== SUBQUERIES IN JOIN CONDITIONS =====

  @ParameterizedTest
  @MethodSource("subqueriesInJoinConditionsArgs")
  void subqueriesInJoinConditions(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueriesInJoinConditionsArgs() {
    return Stream.of(
        Arguments.of(
            "SELECT * FROM orders o JOIN customers c ON o.customer_id = c.id AND o.amount >"
                + " (SELECT AVG(amount) FROM orders WHERE customer_id = c.id)",
            "SELECT orders customers SELECT orders"),
        Arguments.of(
            "SELECT * FROM employees e LEFT JOIN departments d ON e.dept_id = d.id AND EXISTS"
                + " (SELECT 1 FROM projects WHERE dept_id = d.id)",
            "SELECT employees departments SELECT projects"),
        // Self-referencing subquery in ON clause
        Arguments.of(
            "SELECT * FROM orders o JOIN order_items i ON o.id = i.order_id AND i.price > (SELECT"
                + " AVG(price) FROM order_items WHERE order_id = o.id)",
            "SELECT orders order_items SELECT order_items"),
        // Multiple subqueries in join condition
        Arguments.of(
            "SELECT * FROM t1 JOIN t2 ON t1.a IN (SELECT x FROM t3) AND t2.b IN (SELECT y FROM"
                + " t4)",
            "SELECT t1 t2 SELECT t3 SELECT t4"),
        // Correlated subquery referencing both join tables
        Arguments.of(
            "SELECT * FROM customers c JOIN orders o ON c.id = o.cust_id AND EXISTS (SELECT 1 FROM"
                + " vip WHERE vip.cust_id = c.id AND vip.order_id = o.id)",
            "SELECT customers orders SELECT vip"));
  }

  // ===== SUBQUERIES IN CLAUSE POSITIONS =====

  @ParameterizedTest
  @MethodSource("subqueriesInClausePositionsArgs")
  void subqueriesInClausePositions(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> subqueriesInClausePositionsArgs() {
    return Stream.of(
        // HAVING
        Arguments.of(
            "SELECT department_id, COUNT(*) FROM employees GROUP BY department_id HAVING COUNT(*)"
                + " > (SELECT AVG(cnt) FROM (SELECT COUNT(*) as cnt FROM employees GROUP BY"
                + " department_id) AS dept_counts)",
            "SELECT employees SELECT SELECT employees"),
        Arguments.of(
            "SELECT category, SUM(price) FROM products GROUP BY category HAVING SUM(price) >"
                + " (SELECT AVG(total) FROM category_totals)",
            "SELECT products SELECT category_totals"),
        // ORDER BY
        Arguments.of(
            "SELECT * FROM products ORDER BY (SELECT AVG(rating) FROM reviews WHERE product_id ="
                + " products.id) DESC",
            "SELECT products SELECT reviews"),
        Arguments.of(
            "SELECT * FROM employees ORDER BY (SELECT COUNT(*) FROM projects WHERE manager_id ="
                + " employees.id)",
            "SELECT employees SELECT projects"),
        // BETWEEN
        Arguments.of(
            "SELECT * FROM products WHERE price BETWEEN (SELECT MIN(price) FROM products) AND"
                + " (SELECT MAX(price) FROM products)",
            "SELECT products SELECT products SELECT products"),
        // LIMIT (PostgreSQL/MySQL)
        Arguments.of(
            "SELECT * FROM users LIMIT (SELECT setting FROM config WHERE key = ?)",
            "SELECT users SELECT config"),
        // OFFSET
        Arguments.of(
            "SELECT * FROM users OFFSET (SELECT page_size FROM config) ROWS",
            "SELECT users SELECT config"),
        // Table function argument
        Arguments.of(
            "SELECT * FROM GENERATE_SERIES(1, (SELECT MAX(id) FROM users))",
            "SELECT GENERATE_SERIES SELECT users"),
        // COALESCE
        Arguments.of(
            "SELECT COALESCE((SELECT name FROM t1 WHERE id = ?), 'default') FROM dual",
            "SELECT SELECT t1 dual"),
        Arguments.of(
            "SELECT COALESCE((SELECT a FROM t1), (SELECT b FROM t2), (SELECT c FROM t3)) FROM dual",
            "SELECT SELECT t1 SELECT t2 SELECT t3 dual"));
  }

  // ===== CTEs (WITH clause) =====

  @ParameterizedTest
  @MethodSource("ctesArgs")
  void ctes(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> ctesArgs() {
    return Stream.of(
        // CTE referencing another CTE — final SELECT references only CTE "cte2" (anonymous
        // table per spec: operation with no target is preserved)
        Arguments.of(
            "WITH cte1 AS (SELECT * FROM t1), cte2 AS (SELECT * FROM cte1 JOIN t2 ON cte1.id ="
                + " t2.id) SELECT * FROM cte2",
            "SELECT t1 SELECT t2 SELECT"),
        // Multiple CTEs without FROM — each CTE and the final query are target-less SELECTs
        Arguments.of(
            "WITH a AS (SELECT 1), b AS (SELECT 2), c AS (SELECT 3) SELECT * FROM a, b, c",
            "SELECT SELECT SELECT SELECT"),
        // CTE with INSERT — final SELECT references only CTE "new_data" (anonymous table)
        Arguments.of(
            "WITH new_data AS (SELECT * FROM staging WHERE processed = ?) INSERT INTO archive"
                + " SELECT * FROM new_data",
            "SELECT staging INSERT archive SELECT"),
        // CTE with UPDATE — subqueries reference only CTE "updates" (anonymous table per spec)
        Arguments.of(
            "WITH updates AS (SELECT id, new_value FROM temp) UPDATE target SET value = (SELECT"
                + " new_value FROM updates WHERE updates.id = target.id) WHERE EXISTS (SELECT 1"
                + " FROM updates WHERE updates.id = target.id)",
            "SELECT temp UPDATE target SELECT SELECT"),
        // CTE with DELETE — subquery references only CTE "to_delete" (anonymous table per spec)
        Arguments.of(
            "WITH to_delete AS (SELECT id FROM old_records WHERE date < ?) DELETE FROM records"
                + " WHERE id IN (SELECT id FROM to_delete)",
            "SELECT old_records DELETE records SELECT"),
        // Nested CTE references — all CTE names filtered, each SELECT is a target-less operation
        Arguments.of(
            "WITH a AS (SELECT 1 as x), b AS (SELECT x FROM a), c AS (SELECT x FROM b) SELECT *"
                + " FROM c",
            "SELECT SELECT SELECT SELECT"),
        // CTE shadowing a real table name — final SELECT references CTE (anonymous table per spec)
        Arguments.of(
            "WITH users AS (SELECT * FROM active_users) SELECT * FROM users",
            "SELECT active_users SELECT"));
  }

  // ===== SET OPERATIONS =====

  @ParameterizedTest
  @MethodSource("setOperationsArgs")
  void setOperations(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> setOperationsArgs() {
    return Stream.of(
        // INTERSECT
        Arguments.of(
            "SELECT user_id FROM premium_users INTERSECT SELECT user_id FROM active_users",
            "SELECT premium_users SELECT active_users"),
        // EXCEPT
        Arguments.of(
            "SELECT email FROM all_users EXCEPT SELECT email FROM unsubscribed",
            "SELECT all_users SELECT unsubscribed"),
        // UNION with parentheses
        Arguments.of(
            "(SELECT id FROM users) UNION (SELECT id FROM customers)",
            "SELECT users SELECT customers"),
        // Complex set operations with parentheses
        Arguments.of(
            "(SELECT * FROM t1 UNION SELECT * FROM t2) INTERSECT (SELECT * FROM t3 UNION SELECT *"
                + " FROM t4)",
            "SELECT t1 SELECT t2 SELECT t3 SELECT t4"),
        // MINUS (Oracle)
        Arguments.of("SELECT id FROM t1 MINUS SELECT id FROM t2", "SELECT t1 SELECT t2"));
  }

  // ===== WINDOW FUNCTIONS =====

  @ParameterizedTest
  @MethodSource("windowFunctionsArgs")
  void windowFunctions(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> windowFunctionsArgs() {
    return Stream.of(
        // Basic PARTITION BY with alias
        Arguments.of(
            "SELECT name, department, salary, AVG(salary) OVER (PARTITION BY department) as"
                + " avg_dept_salary FROM employees",
            "SELECT employees"),
        // Named window
        Arguments.of(
            "SELECT id, SUM(val) OVER w FROM data WINDOW w AS (PARTITION BY cat)", "SELECT data"),
        // ROWS BETWEEN
        Arguments.of(
            "SELECT SUM(val) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)"
                + " FROM data",
            "SELECT data"),
        // Multiple window functions
        Arguments.of(
            "SELECT ROW_NUMBER() OVER (ORDER BY a), RANK() OVER (PARTITION BY b ORDER BY c) FROM"
                + " tbl",
            "SELECT tbl"),
        // Subquery in PARTITION BY (should not confuse table extraction)
        Arguments.of(
            "SELECT SUM(amount) OVER (PARTITION BY (SELECT type FROM types WHERE types.id ="
                + " sales.type_id)) FROM sales",
            "SELECT SELECT types sales"));
  }

  // ===== EXPRESSIONS (CAST, CASE, ROW VALUES) =====

  @ParameterizedTest
  @MethodSource("expressionsArgs")
  void expressions(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> expressionsArgs() {
    return Stream.of(
        // CAST
        Arguments.of("SELECT CAST(price AS DECIMAL(10,2)) FROM products", "SELECT products"),
        Arguments.of(
            "SELECT CAST((SELECT MAX(amount) FROM orders) AS INTEGER) FROM dual",
            "SELECT SELECT orders dual"),
        Arguments.of(
            "SELECT name, CAST(created_at AS DATE) FROM users WHERE CAST(status AS INTEGER) = ?",
            "SELECT users"),
        // Simple CASE
        Arguments.of(
            "SELECT CASE WHEN status = ? THEN 'Active' ELSE 'Inactive' END FROM users",
            "SELECT users"),
        // CASE with subquery in condition
        Arguments.of(
            "SELECT CASE WHEN price > (SELECT AVG(price) FROM products) THEN 'High' ELSE 'Low'"
                + " END FROM products",
            "SELECT SELECT products products"),
        // CASE with subqueries in THEN
        Arguments.of(
            "SELECT CASE type WHEN 'A' THEN (SELECT COUNT(*) FROM type_a) WHEN 'B' THEN (SELECT"
                + " COUNT(*) FROM type_b) ELSE 0 END FROM items",
            "SELECT SELECT type_a SELECT type_b items"),
        // Searched CASE with subquery in discriminant and branches
        Arguments.of(
            "SELECT CASE (SELECT type FROM config) WHEN 'A' THEN (SELECT v FROM a) WHEN 'B' THEN"
                + " (SELECT v FROM b) END FROM dual",
            "SELECT SELECT config SELECT a SELECT b dual"),
        // Row value constructors
        Arguments.of(
            "SELECT * FROM orders WHERE (customer_id, product_id) = (?, ?)", "SELECT orders"),
        Arguments.of(
            "SELECT * FROM users WHERE (first_name, last_name) IN (SELECT first_name, last_name"
                + " FROM vip_users)",
            "SELECT users SELECT vip_users"));
  }

  // ===== CLAUSES (PAGINATION, GROUP BY, SELECT INTO, RETURNING, SPECIAL) =====

  @ParameterizedTest
  @MethodSource("clausesArgs")
  void clauses(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> clausesArgs() {
    return Stream.of(
        // FETCH FIRST / NEXT
        Arguments.of(
            "SELECT * FROM users ORDER BY created_at OFFSET 10 ROWS FETCH FIRST 20 ROWS ONLY",
            "SELECT users"),
        Arguments.of(
            "SELECT * FROM products ORDER BY price FETCH NEXT 10 ROWS ONLY", "SELECT products"),
        Arguments.of("SELECT * FROM orders OFFSET 5 ROWS FETCH NEXT 1 ROW ONLY", "SELECT orders"),
        // LIMIT / OFFSET
        Arguments.of("SELECT * FROM users LIMIT 10 OFFSET 5", "SELECT users"),
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
            "SELECT employees"),
        // SELECT INTO
        Arguments.of("SELECT * INTO temp_table FROM users WHERE active = ?", "SELECT users"),
        Arguments.of(
            "SELECT u.name, COUNT(o.id) INTO summary_table FROM users u LEFT JOIN orders o ON"
                + " u.id = o.user_id GROUP BY u.name",
            "SELECT users orders"),
        // RETURNING
        Arguments.of("INSERT INTO users (name) VALUES (?) RETURNING id", "INSERT users"),
        Arguments.of("DELETE FROM users WHERE id = ? RETURNING *", "DELETE users"),
        Arguments.of("UPDATE users SET name = ? WHERE id = ? RETURNING *", "UPDATE users"),
        // FOR UPDATE / FOR SHARE / SKIP LOCKED
        Arguments.of("SELECT * FROM users FOR UPDATE OF users.name NOWAIT", "SELECT users"),
        Arguments.of("SELECT * FROM users FOR SHARE", "SELECT users"),
        Arguments.of("SELECT * FROM users FOR UPDATE SKIP LOCKED", "SELECT users"),
        // FETCH FIRST with PERCENT
        Arguments.of("SELECT * FROM users FETCH FIRST 10 PERCENT ROWS ONLY", "SELECT users"),
        // TOP with PERCENT (SQL Server)
        Arguments.of("SELECT TOP 10 PERCENT * FROM users", "SELECT users"),
        // DISTINCT ON (PostgreSQL)
        Arguments.of("SELECT DISTINCT ON (department) * FROM employees", "SELECT employees"));
  }

  // ===== VALUES =====

  @ParameterizedTest
  @MethodSource("valuesArgs")
  void valuesClauses(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> valuesArgs() {
    return Stream.of(
        // INSERT with subquery in VALUES
        Arguments.of(
            "INSERT INTO t1 (col) VALUES ((SELECT MAX(col) FROM t2))", "INSERT t1 SELECT t2"),
        // VALUES joined with a real table — only the real table appears
        Arguments.of(
            "SELECT * FROM (VALUES (1), (2)) AS t(id) JOIN users u ON t.id = u.id", "SELECT users"),
        // VALUES in UNION
        Arguments.of("SELECT id FROM users UNION VALUES (1), (2)", "SELECT users"));
  }

  // ===== IDENTIFIERS AND TABLE REFERENCES =====

  @ParameterizedTest
  @MethodSource("identifiersAndTableReferencesArgs")
  void identifiersAndTableReferences(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> identifiersAndTableReferencesArgs() {
    return Stream.of(
        // Quoted identifiers that look like keywords
        Arguments.of("SELECT * FROM \"SELECT\" WHERE \"FROM\" = ?", "SELECT \"SELECT\""),
        Arguments.of("SELECT * FROM `SELECT` WHERE `FROM` = ?", "SELECT `SELECT`"),
        Arguments.of("SELECT * FROM [SELECT] WHERE [FROM] = ?", "SELECT [SELECT]"),
        // Quoted identifier with dots
        Arguments.of("SELECT * FROM \"table.name\"", "SELECT \"table.name\""),
        // Mixed quoting styles
        Arguments.of(
            "SELECT * FROM \"schema\".`table` JOIN [other] ON \"schema\".`table`.id = [other].id",
            "SELECT \"schema\".`table` [other]"),
        // Schema patterns
        Arguments.of("SELECT * FROM db.schema.table", "SELECT db.schema.table"),
        Arguments.of(
            "SELECT * FROM schema1.t1 JOIN t2 ON schema1.t1.id = t2.id", "SELECT schema1.t1 t2"),
        // Parenthesized table names
        Arguments.of("SELECT * FROM (t1), (t2)", "SELECT t1 t2"),
        // Multiple implicit joins (comma-separated)
        Arguments.of(
            "SELECT * FROM t1, t2, t3, t4, t5 WHERE t1.id = t2.t1_id", "SELECT t1 t2 t3 t4 t5"),
        Arguments.of("SELECT * FROM t1, t2 JOIN t3 ON t2.id = t3.t2_id, t4", "SELECT t1 t2 t3 t4"),
        // Table alias matching another table name
        Arguments.of(
            "SELECT * FROM users orders JOIN orders o ON orders.id = o.user_id",
            "SELECT users orders"));
  }

  // ===== ALIAS EDGE CASES =====

  @ParameterizedTest
  @MethodSource("aliasEdgeCasesArgs")
  void aliasEdgeCases(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> aliasEdgeCasesArgs() {
    return Stream.of(
        // Trailing JOIN keyword with no following table
        Arguments.of("SELECT * FROM users join", "SELECT users"),
        // Table name same as alias of previous table
        Arguments.of(
            "SELECT * FROM orders users JOIN users u ON orders.user_id = u.id",
            "SELECT orders users"),
        // Very long alias
        Arguments.of(
            "SELECT * FROM users AS this_is_a_very_long_alias_name_that_exceeds_normal_length",
            "SELECT users"));
  }

  // ===== LEXICAL EDGE CASES (COMMENTS, WHITESPACE, PARAMETERS) =====

  @ParameterizedTest
  @MethodSource("lexicalEdgeCasesArgs")
  void lexicalEdgeCases(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> lexicalEdgeCasesArgs() {
    return Stream.of(
        // Nested-looking block comment
        Arguments.of("SELECT /* outer /* inner */ still comment */ * FROM users", "SELECT users"),
        // Dollar-quoted strings (PostgreSQL) - unterminated
        Arguments.of("SELECT * FROM users WHERE name = $tag$unterminated", "SELECT users"));
  }

  // ===== EMPTY STATEMENTS AND SEMICOLONS =====

  @ParameterizedTest
  @MethodSource("statementsAndSemicolonsArgs")
  void statementsAndSemicolons(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> statementsAndSemicolonsArgs() {
    return Stream.of(
        // Leading semicolons
        Arguments.of("; SELECT * FROM users", "SELECT users"),
        Arguments.of(";; SELECT * FROM t1", "SELECT t1"),
        // Empty statements between real statements
        Arguments.of("; ; SELECT * FROM t1; ; SELECT * FROM t2; ;", "SELECT t1; SELECT t2"));
  }

  // ===== VENDOR-SPECIFIC SYNTAX =====

  @ParameterizedTest
  @MethodSource("vendorSpecificSyntaxArgs")
  void vendorSpecificSyntax(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> vendorSpecificSyntaxArgs() {
    return Stream.of(
        // PIVOT (SQL Server/Oracle)
        Arguments.of(
            "SELECT * FROM sales PIVOT (SUM(amount) FOR quarter IN ('Q1', 'Q2', 'Q3', 'Q4'))",
            "SELECT sales"),
        // UNPIVOT
        Arguments.of(
            "SELECT * FROM wide_table UNPIVOT (value FOR col_name IN (col1, col2, col3))",
            "SELECT wide_table"),
        // TABLESAMPLE
        Arguments.of("SELECT * FROM large_table TABLESAMPLE SYSTEM (10)", "SELECT large_table"),
        Arguments.of(
            "SELECT * FROM large_table TABLESAMPLE BERNOULLI (1) REPEATABLE (42)",
            "SELECT large_table"),
        // FOR XML (SQL Server)
        Arguments.of("SELECT * FROM users FOR XML PATH", "SELECT users"),
        // FOR JSON (SQL Server)
        Arguments.of("SELECT * FROM users FOR JSON AUTO", "SELECT users"),
        // MATCH_RECOGNIZE (Oracle)
        Arguments.of(
            "SELECT * FROM ticker MATCH_RECOGNIZE (ORDER BY tstamp MEASURES A.tstamp AS start_t"
                + " PATTERN (A B* C) DEFINE A AS A.price > 10)",
            "SELECT ticker"),
        // MODEL clause (Oracle)
        Arguments.of(
            "SELECT * FROM sales MODEL DIMENSION BY (product) MEASURES (amount) RULES"
                + " (amount['Total'] = amount['A'] + amount['B'])",
            "SELECT sales"),
        // CONNECT BY (Oracle)
        Arguments.of(
            "SELECT * FROM employees START WITH manager_id IS NULL CONNECT BY PRIOR employee_id ="
                + " manager_id",
            "SELECT employees"));
  }

  // ===== DISABLED: INVALID SQL =====
  // Using reserved keywords (from, select, where) as unquoted column names
  // is not valid in any major database. Valid SQL requires quoting.

  @Disabled
  @ParameterizedTest
  @MethodSource("keywordsAsUnquotedColumnNamesArgs")
  void keywordsAsColumnNames(String sql, String expectedSummary) {
    SqlQuery result = sanitize(sql);
    assertThat(result.getQuerySummary()).isEqualTo(expectedSummary);
  }

  private static Stream<Arguments> keywordsAsUnquotedColumnNamesArgs() {
    return Stream.of(
        Arguments.of("SELECT from, select, where FROM keywords", "SELECT keywords"),
        Arguments.of("INSERT INTO t (from, select) VALUES (?, ?)", "INSERT t"));
  }
}
