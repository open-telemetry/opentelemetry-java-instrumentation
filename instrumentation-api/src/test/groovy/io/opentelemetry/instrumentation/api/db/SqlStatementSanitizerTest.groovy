/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.db

import spock.lang.Specification
import spock.lang.Unroll

class SqlStatementSanitizerTest extends Specification {

  def "normalize #originalSql"() {
    setup:
    def actualSanitized = SqlStatementSanitizer.sanitize(originalSql)

    expect:
    actualSanitized.getFullStatement() == sanitizedSql

    where:
    originalSql                                                               | sanitizedSql
    // Numbers
    "SELECT * FROM TABLE WHERE FIELD=1234"                                    | "SELECT * FROM TABLE WHERE FIELD=?"
    "SELECT * FROM TABLE WHERE FIELD = 1234"                                  | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD>=-1234"                                  | "SELECT * FROM TABLE WHERE FIELD>=?"
    "SELECT * FROM TABLE WHERE FIELD<-1234"                                   | "SELECT * FROM TABLE WHERE FIELD<?"
    "SELECT * FROM TABLE WHERE FIELD <.1234"                                  | "SELECT * FROM TABLE WHERE FIELD <?"
    "SELECT 1.2"                                                              | "SELECT ?"
    "SELECT -1.2"                                                             | "SELECT ?"
    "SELECT -1.2e-9"                                                          | "SELECT ?"
    "SELECT 2E+9"                                                             | "SELECT ?"
    "SELECT +0.2"                                                             | "SELECT ?"
    "SELECT .2"                                                               | "SELECT ?"
    "7"                                                                       | "?"
    ".7"                                                                      | "?"
    "-7"                                                                      | "?"
    "+7"                                                                      | "?"
    "SELECT 0x0af764"                                                         | "SELECT ?"
    "SELECT 0xdeadBEEF"                                                       | "SELECT ?"
    "SELECT * FROM \"TABLE\""                                                 | "SELECT * FROM \"TABLE\""

    // Not numbers but could be confused as such
    "SELECT A + B"                                                            | "SELECT A + B"
    "SELECT -- comment"                                                       | "SELECT -- comment"
    "SELECT * FROM TABLE123"                                                  | "SELECT * FROM TABLE123"
    "SELECT FIELD2 FROM TABLE_123 WHERE X<>7"                                 | "SELECT FIELD2 FROM TABLE_123 WHERE X<>?"

    // Semi-nonsensical almost-numbers to elide or not
    "SELECT --83--...--8e+76e3E-1"                                            | "SELECT ?"
    "SELECT DEADBEEF"                                                         | "SELECT DEADBEEF"
    "SELECT 123-45-6789"                                                      | "SELECT ?"
    "SELECT 1/2/34"                                                           | "SELECT ?/?/?"

    // Basic ' strings
    "SELECT * FROM TABLE WHERE FIELD = ''"                                    | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'words and spaces'"                    | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = ' an escaped '' quote mark inside'"    | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\\\\'"                                | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"inside doubles\"'"                  | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = '\"\$\$\$\$\"'"                        | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = 'a single \" doublequote inside'"      | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Some databases allow using dollar-quoted strings
    "SELECT * FROM TABLE WHERE FIELD = \$\$\$\$"                              | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$words and spaces\$\$"              | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$quotes '\" inside\$\$"             | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$\"''\"\$\$"                        | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \$\$\\\\\$\$"                          | "SELECT * FROM TABLE WHERE FIELD = ?"

    // Unicode, including a unicode identifier with a trailing number
    "SELECT * FROM TABLE\u09137 WHERE FIELD = '\u0194'"                       | "SELECT * FROM TABLE\u09137 WHERE FIELD = ?"

    // whitespace normalization
    "SELECT    *    \t\r\nFROM  TABLE WHERE FIELD1 = 12344 AND FIELD2 = 5678" | "SELECT * FROM TABLE WHERE FIELD1 = ? AND FIELD2 = ?"

    // hibernate/jpa query language
    "FROM TABLE WHERE FIELD=1234"                                             | "FROM TABLE WHERE FIELD=?"
  }

  def "normalize couchbase #originalSql"() {
    setup:
    def actualSanitized = SqlStatementSanitizer.sanitize(originalSql, SqlDialect.COUCHBASE)

    expect:
    actualSanitized.getFullStatement() == sanitizedSql

    where:
    originalSql                                                                | sanitizedSql
    // Some databases support/encourage " instead of ' with same escape rules
    "SELECT * FROM TABLE WHERE FIELD = \"\""                                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"words and spaces'\""                  | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \" an escaped \"\" quote mark inside\"" | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"\\\\\""                               | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"'inside singles'\""                   | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"'\$\$\$\$'\""                         | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD = \"a single ' singlequote inside\""      | "SELECT * FROM TABLE WHERE FIELD = ?"
  }

  @Unroll
  def "should simplify #sql"() {
    expect:
    SqlStatementSanitizer.sanitize(sql) == expected

    where:
    sql                                                               | expected
    // Select
    'SELECT x, y, z FROM schema.table'                                | SqlStatementInfo.create(sql, 'SELECT', 'schema.table')
    'SELECT x, y, z FROM `schema.table`'                              | SqlStatementInfo.create(sql, 'SELECT', 'schema.table')
    'SELECT x, y, z FROM "schema.table"'                              | SqlStatementInfo.create(sql, 'SELECT', 'schema.table')
    'WITH subquery as (select a from b) SELECT x, y, z FROM table'    | SqlStatementInfo.create(sql, 'SELECT', null)
    'SELECT x, y, (select a from b) as z FROM table'                  | SqlStatementInfo.create(sql, 'SELECT', null)
    'select delete, insert into, merge, update from table'            | SqlStatementInfo.create(sql, 'SELECT', 'table')
    'select col /* from table2 */ from table'                         | SqlStatementInfo.create(sql, 'SELECT', 'table')
    'select col from table join anotherTable'                         | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from (select * from anotherTable)'                    | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from (select * from anotherTable) alias'              | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table1 union select col from table2'             | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table where col in (select * from anotherTable)' | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table1, table2'                                  | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table1 t1, table2 t2'                            | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table1 as t1, table2 as t2'                      | SqlStatementInfo.create(sql, 'SELECT', null)
    'select col from table where col in (1, 2, 3)'                    | SqlStatementInfo.create('select col from table where col in (?, ?, ?)', 'SELECT', 'table')
    'select col from table order by col, col2'                        | SqlStatementInfo.create(sql, 'SELECT', 'table')
    'select ąś∂ń© from źćļńĶ order by col, col2'                      | SqlStatementInfo.create(sql, 'SELECT', 'źćļńĶ')
    'select 12345678'                                                 | SqlStatementInfo.create('select ?', 'SELECT', null)
    '/* update comment */ select * from table1'                       | SqlStatementInfo.create(sql, 'SELECT', 'table1')
    'select /*((*/abc from table'                                     | SqlStatementInfo.create(sql, 'SELECT', 'table')
    'SeLeCT * FrOm TAblE'                                             | SqlStatementInfo.create(sql, 'SELECT', 'TAblE')
    // hibernate/jpa
    'FROM schema.table'                                               | SqlStatementInfo.create(sql, 'SELECT', 'schema.table')
    '/* update comment */ from table1'                                | SqlStatementInfo.create(sql, 'SELECT', 'table1')
    // Insert
    ' insert into table where lalala'                                 | SqlStatementInfo.create(sql, 'INSERT', 'table')
    'insert insert into table where lalala'                           | SqlStatementInfo.create(sql, 'INSERT', 'table')
    'insert into db.table where lalala'                               | SqlStatementInfo.create(sql, 'INSERT', 'db.table')
    'insert into `db.table` where lalala'                             | SqlStatementInfo.create(sql, 'INSERT', 'db.table')
    'insert into "db.table" where lalala'                             | SqlStatementInfo.create(sql, 'INSERT', 'db.table')
    'insert without i-n-t-o'                                          | SqlStatementInfo.create(sql, 'INSERT', null)
    // Delete
    'delete from table where something something'                     | SqlStatementInfo.create(sql, 'DELETE', 'table')
    'delete from `table` where something something'                   | SqlStatementInfo.create(sql, 'DELETE', 'table')
    'delete from "table" where something something'                   | SqlStatementInfo.create(sql, 'DELETE', 'table')
    'delete from 12345678'                                            | SqlStatementInfo.create('delete from ?', 'DELETE', null)
    'delete   ((('                                                    | SqlStatementInfo.create('delete (((', 'DELETE', null)
    // Update
    'update table set answer=42'                                      | SqlStatementInfo.create('update table set answer=?', 'UPDATE', 'table')
    'update `table` set answer=42'                                    | SqlStatementInfo.create('update `table` set answer=?', 'UPDATE', 'table')
    'update "table" set answer=42'                                    | SqlStatementInfo.create('update "table" set answer=?', 'UPDATE', 'table')
    'update /*table'                                                  | SqlStatementInfo.create(sql, 'UPDATE', null)
    // Merge
    'merge into table'                                                | SqlStatementInfo.create(sql, 'MERGE', 'table')
    'merge into `table`'                                              | SqlStatementInfo.create(sql, 'MERGE', 'table')
    'merge into "table"'                                              | SqlStatementInfo.create(sql, 'MERGE', 'table')
    'merge table (into is optional in some dbs)'                      | SqlStatementInfo.create(sql, 'MERGE', 'table')
    'merge (into )))'                                                 | SqlStatementInfo.create(sql, 'MERGE', null)
    // Unknown operation
    'and now for something completely different'                      | SqlStatementInfo.create(sql, null, null)
    ''                                                                | SqlStatementInfo.create(sql, null, null)
    null                                                              | SqlStatementInfo.create(sql, null, null)
  }

  def "very long SELECT statements don't cause problems"() {
    given:
    def sb = new StringBuilder("SELECT * FROM table WHERE")
    for (int i = 0; i < 2000; i++) {
      sb.append(" column").append(i).append("=123 and")
    }
    def query = sb.toString()

    expect:
    def sanitizedQuery = query.replace('=123', '=?').substring(0, AutoSqlSanitizer.LIMIT)
    SqlStatementSanitizer.sanitize(query) == SqlStatementInfo.create(sanitizedQuery, "SELECT", "table")
  }

  def "lots and lots of ticks don't cause stack overflow or long runtimes"() {
    setup:
    String s = "'"
    for (int i = 0; i < 10000; i++) {
      assert SqlStatementSanitizer.sanitize(s) != null
      s += "'"
    }
  }

  def "very long numbers don't cause a problem"() {
    setup:
    String s = ""
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i)
    }
    assert "?" == SqlStatementSanitizer.sanitize(s).getFullStatement()
  }

  def "very long numbers at end of table name don't cause problem"() {
    setup:
    String s = "A"
    for (int i = 0; i < 10000; i++) {
      s += String.valueOf(i)
    }
    assert s.substring(0, AutoSqlSanitizer.LIMIT) == SqlStatementSanitizer.sanitize(s).getFullStatement()
  }

  def "test 32k truncation"() {
    setup:
    StringBuffer s = new StringBuffer()
    for (int i = 0; i < 10000; i++) {
      s.append("SELECT * FROM TABLE WHERE FIELD = 1234 AND ")
    }
    String sanitized = SqlStatementSanitizer.sanitize(s.toString()).getFullStatement()
    System.out.println(sanitized.length())
    assert sanitized.length() <= AutoSqlSanitizer.LIMIT
    assert !sanitized.contains("1234")
  }

  def "random bytes don't cause exceptions or timeouts"() {
    setup:
    Random r = new Random(0)
    for (int i = 0; i < 1000; i++) {
      StringBuffer sb = new StringBuffer()
      for (int c = 0; c < 1000; c++) {
        sb.append((char) r.nextInt((int) Character.MAX_VALUE))
      }
      SqlStatementSanitizer.sanitize(sb.toString())
    }
  }
}
