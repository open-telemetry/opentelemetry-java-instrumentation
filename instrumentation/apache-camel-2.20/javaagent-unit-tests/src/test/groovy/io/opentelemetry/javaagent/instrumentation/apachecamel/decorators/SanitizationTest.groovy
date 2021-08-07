/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators

import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification
import spock.lang.Unroll

class SanitizationTest extends Specification {

  @Unroll
  def "sanitize cql #originalCql"() {

    setup:
    def decorator = new DbSpanDecorator("cql", "")
    def exchange = Mock(Exchange) {
      getIn() >> Mock(Message) {
        getHeader("CamelCqlQuery") >> originalCql
      }
    }
    def actualSanitized = decorator.getStatement(exchange, null)

    expect:
    actualSanitized == sanitizedCql

    where:
    originalCql                                                         | sanitizedCql
    "FROM TABLE WHERE FIELD>=-1234"                                     | "FROM TABLE WHERE FIELD>=?"
    "SELECT Name, Phone.Number FROM Contact WHERE Address.State = 'NY'" | "SELECT Name, Phone.Number FROM Contact WHERE Address.State = ?"
    "FROM col WHERE @Tag='Something'"                                   | "FROM col WHERE @Tag=?"
  }

  @Unroll
  def "sanitize jdbc #originalSql"() {

    setup:
    def decorator = new DbSpanDecorator("jdbc", "")
    def exchange = Mock(Exchange) {
      getIn() >> Mock(Message) {
        getBody() >> originalSql
      }
    }
    def actualSanitized = decorator.getStatement(exchange, null)

    expect:
    actualSanitized == sanitizedSql

    where:
    originalSql                                       | sanitizedSql
    "SELECT 3"                                        | "SELECT ?"
    "SELECT * FROM TABLE WHERE FIELD = 1234"          | "SELECT * FROM TABLE WHERE FIELD = ?"
    "SELECT * FROM TABLE WHERE FIELD<-1234"           | "SELECT * FROM TABLE WHERE FIELD<?"
    "SELECT col1 AS col2 FROM users WHERE field=1234" | "SELECT col1 AS col2 FROM users WHERE field=?"
  }

  @Unroll
  def "sanitize sql #originalSql"() {

    setup:
    def decorator = new DbSpanDecorator("sql", "")
    def exchange = Mock(Exchange) {
      getIn() >> Mock(Message) {
        getHeader("CamelSqlQuery") >> originalSql
      }
    }
    def actualSanitized = decorator.getStatement(exchange, null)

    expect:
    actualSanitized == sanitizedSql

    where:
    originalSql                                      | sanitizedSql
    "SELECT * FROM table WHERE col1=1234 AND col2>3" | "SELECT * FROM table WHERE col1=? AND col2>?"
    "UPDATE table SET col=12"                        | "UPDATE table SET col=?"
    'insert into table where col=321'                | 'insert into table where col=?'
  }


}
