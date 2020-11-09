/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db.normalizer

import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo
import spock.lang.Specification
import spock.lang.Unroll

class SqlStatementInfoExtractorTest extends Specification {

  @Unroll
  def "should simplify #sql"() {
    expect:
    SqlStatementInfoExtractor.extract(sql) == expected

    where:
    sql                                                               | expected
    // Select
    'SELECT x, y, z FROM schema.table'                                | new SqlStatementInfo(sql, 'SELECT', 'schema.table')
    'WITH subquery as (select a from b) SELECT x, y, z FROM table'    | new SqlStatementInfo(sql, 'SELECT', null)
    'SELECT x, y, (select a from b) as z FROM table'                  | new SqlStatementInfo(sql, 'SELECT', null)
    'select delete, insert into, merge, update from table'            | new SqlStatementInfo(sql, 'SELECT', 'table')
    'select col /* from table2 */ from table'                         | new SqlStatementInfo(sql, 'SELECT', 'table')
    'select col from table join anotherTable'                         | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from (select * from anotherTable)'                    | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from (select * from anotherTable) alias'              | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table1 union select col from table2'             | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table where col in (select * from anotherTable)' | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table1, table2'                                  | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table1 t1, table2 t2'                            | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table1 as t1, table2 as t2'                      | new SqlStatementInfo(sql, 'SELECT', null)
    'select col from table where col in (1, 2, 3)'                    | new SqlStatementInfo(sql, 'SELECT', 'table')
    'select col from table order by col, col2'                        | new SqlStatementInfo(sql, 'SELECT', 'table')
    'select ąś∂ń© from źćļńĶ order by col, col2'                      | new SqlStatementInfo(sql, 'SELECT', 'źćļńĶ')
    'select 12345678'                                                 | new SqlStatementInfo(sql, 'SELECT', null)
    // Insert
    ' insert into table where lalala'                                 | new SqlStatementInfo(sql.trim(), 'INSERT', 'table')
    'insert insert into table where lalala'                           | new SqlStatementInfo(sql, 'INSERT', 'table')
    'insert into db.table where lalala'                               | new SqlStatementInfo(sql, 'INSERT', 'db.table')
    'insert without i-n-t-o'                                          | new SqlStatementInfo(sql, 'INSERT', null)
    // Delete
    'delete from table where something something'                     | new SqlStatementInfo(sql, 'DELETE', 'table')
    'delete from 12345678'                                            | new SqlStatementInfo(sql, 'DELETE', null)
    'delete   ((('                                                    | new SqlStatementInfo(sql, 'DELETE', null)
    // Update
    'update table set answer=42'                                      | new SqlStatementInfo(sql, 'UPDATE', 'table')
    'update /*table'                                                  | new SqlStatementInfo(sql, 'UPDATE', null)
    // Merge
    'merge into table'                                                | new SqlStatementInfo(sql, 'MERGE', 'table')
    'merge table (into is optional in some dbs)'                      | new SqlStatementInfo(sql, 'MERGE', 'table')
    'merge (into )))'                                                 | new SqlStatementInfo(sql, 'MERGE', null)
    // Unknown operation
    'and now for something completely different'                      | new SqlStatementInfo(sql, null, null)
    ''                                                                | new SqlStatementInfo(sql, null, null)
    null                                                              | new SqlStatementInfo(sql, null, null)
  }

  def "very long SELECT statements don't cause problems"() {
    given:
    def sb = new StringBuilder("SELECT * FROM table WHERE")
    for (int i = 0; i < 2000; i++) {
      sb.append(" column").append(i).append("=? and")
    }
    def query = sb.toString()

    expect:
    SqlStatementInfoExtractor.extract(query) == new SqlStatementInfo(query, "SELECT", "table")
  }

  def "random bytes don't cause exceptions or timeouts"() {
    setup:
    def r = new Random(0)
    for (int i = 0; i < 1000; i++) {
      def sb = new StringBuilder()
      for (int c = 0; c < 1000; c++) {
        sb.append((char) r.nextInt((int) Character.MAX_VALUE))
      }
      SqlStatementInfoExtractor.extract(sb.toString())
    }
  }
}
