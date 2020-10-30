package io.opentelemetry.javaagent.instrumentation.api.db.normalizer

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
    'SELECT x, y, z FROM db.schema.table'                          | 'SELECT FROM db.schema.table'
    'WITH subquery as (select a from b) SELECT x, y, z FROM table' | 'SELECT FROM table'
    'SELECT x, y, (select a from b) as z FROM table'               | 'SELECT FROM table'
    'select delete, insert into, merge, update from table'         | 'SELECT FROM table'
    'select col /* from table2 */ from table'                      | 'SELECT FROM table'
    // Insert
    ' insert into table where lalala'                              | 'INSERT INTO table'
    'insert insert into table where lalala'                        | 'INSERT INTO table'
    'insert into db.table where lalala'                            | 'INSERT INTO db.table'
    'insert without i-n-t-o'                                       | null
    // Delete
    'delete from table where something something'                  | 'DELETE FROM table'
    // Update
    'update table set answer=42'                                   | 'UPDATE table'
    // Merge
    'merge into table'                                             | 'MERGE table'
    'merge table (into is optional in some dbs)'                   | 'MERGE table'
  }
}
