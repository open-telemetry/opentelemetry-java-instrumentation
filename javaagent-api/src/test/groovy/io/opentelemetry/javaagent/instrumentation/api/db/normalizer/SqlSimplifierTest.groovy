/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db.normalizer

import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo
import spock.lang.Specification
import spock.lang.Unroll

class SqlSimplifierTest extends Specification {

  @Unroll
  def "should simplify #sql"() {
    expect:
    SqlSimplifier.simplify(sql) == expected

    where:
    sql                                                            | expected
    // Select
    'SELECT x, y, z FROM schema.table'                             | new SqlStatementInfo('SELECT', 'schema.table')
    'WITH subquery as (select a from b) SELECT x, y, z FROM table' | new SqlStatementInfo('SELECT', 'table')
    'SELECT x, y, (select a from b) as z FROM table'               | new SqlStatementInfo('SELECT', 'table')
    'select delete, insert into, merge, update from table'         | new SqlStatementInfo('SELECT', 'table')
    'select col /* from table2 */ from table'                      | new SqlStatementInfo('SELECT', 'table')
    'select col from table join anotherTable'                      | new SqlStatementInfo('SELECT', null)
    'select col from (select * from anotherTable)'                 | new SqlStatementInfo('SELECT', null)
    'select col from (select * from anotherTable) alias'           | new SqlStatementInfo('SELECT', null)
    'select col from table1 union select col from table2'          | new SqlStatementInfo('SELECT', null)
    // Insert
    ' insert into table where lalala'                              | new SqlStatementInfo('INSERT', 'table')
    'insert insert into table where lalala'                        | new SqlStatementInfo('INSERT', 'table')
    'insert into db.table where lalala'                            | new SqlStatementInfo('INSERT', 'db.table')
    'insert without i-n-t-o'                                       | new SqlStatementInfo('INSERT', null)
    // Delete
    'delete from table where something something'                  | new SqlStatementInfo('DELETE', 'table')
    // Update
    'update table set answer=42'                                   | new SqlStatementInfo('UPDATE', 'table')
    // Merge
    'merge into table'                                             | new SqlStatementInfo('MERGE', 'table')
    'merge table (into is optional in some dbs)'                   | new SqlStatementInfo('MERGE', 'table')
  }
}
