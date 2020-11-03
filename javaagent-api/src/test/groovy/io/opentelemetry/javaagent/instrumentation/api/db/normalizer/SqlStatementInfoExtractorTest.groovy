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
    'SELECT x, y, z FROM schema.table'                                | new SqlStatementInfo('SELECT', 'schema.table')
    'WITH subquery as (select a from b) SELECT x, y, z FROM table'    | new SqlStatementInfo('SELECT', null)
    'SELECT x, y, (select a from b) as z FROM table'                  | new SqlStatementInfo('SELECT', null)
    'select delete, insert into, merge, update from table'            | new SqlStatementInfo('SELECT', 'table')
    'select col /* from table2 */ from table'                         | new SqlStatementInfo('SELECT', 'table')
    'select col from table join anotherTable'                         | new SqlStatementInfo('SELECT', null)
    'select col from (select * from anotherTable)'                    | new SqlStatementInfo('SELECT', null)
    'select col from (select * from anotherTable) alias'              | new SqlStatementInfo('SELECT', null)
    'select col from table1 union select col from table2'             | new SqlStatementInfo('SELECT', null)
    'select col from table where col in (select * from anotherTable)' | new SqlStatementInfo('SELECT', null)
    'select col from table1, table2'                                  | new SqlStatementInfo('SELECT', null)
    'select col from table1 t1, table2 t2'                            | new SqlStatementInfo('SELECT', null)
    'select col from table1 as t1, table2 as t2'                      | new SqlStatementInfo('SELECT', null)
    'select col from table where col in (1, 2, 3)'                    | new SqlStatementInfo('SELECT', 'table')
    'select col from table order by col, col2'                        | new SqlStatementInfo('SELECT', 'table')
    // Insert
    ' insert into table where lalala'                                 | new SqlStatementInfo('INSERT', 'table')
    'insert insert into table where lalala'                           | new SqlStatementInfo('INSERT', 'table')
    'insert into db.table where lalala'                               | new SqlStatementInfo('INSERT', 'db.table')
    'insert without i-n-t-o'                                          | new SqlStatementInfo('INSERT', null)
    // Delete
    'delete from table where something something'                     | new SqlStatementInfo('DELETE', 'table')
    // Update
    'update table set answer=42'                                      | new SqlStatementInfo('UPDATE', 'table')
    // Merge
    'merge into table'                                                | new SqlStatementInfo('MERGE', 'table')
    'merge table (into is optional in some dbs)'                      | new SqlStatementInfo('MERGE', 'table')
    // Unknown operation
    'and now for something completely different'                      | null
  }
}
