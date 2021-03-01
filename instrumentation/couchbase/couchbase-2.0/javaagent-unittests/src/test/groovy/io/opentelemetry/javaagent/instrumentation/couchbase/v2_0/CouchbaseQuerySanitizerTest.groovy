/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0

import com.couchbase.client.java.analytics.AnalyticsQuery
import com.couchbase.client.java.query.N1qlQuery
import com.couchbase.client.java.query.Select
import com.couchbase.client.java.query.dsl.Expression
import com.couchbase.client.java.view.SpatialViewQuery
import com.couchbase.client.java.view.ViewQuery
import spock.lang.Specification
import spock.lang.Unroll

class CouchbaseQuerySanitizerTest extends Specification {
  @Unroll
  def "should normalize #desc query"() {
    when:
    def normalized = CouchbaseQuerySanitizer.sanitize(query).getFullStatement()

    then:
    // the analytics query ends up with trailing ';' in earlier couchbase version, but no trailing ';' in later couchbase version
    normalized.replaceFirst(';$', '') == expected

    where:
    desc           | query                                                                                          | expected
    "plain string" | "SELECT field1 FROM `test` WHERE field2 = 'asdf'"                                              | "SELECT field1 FROM `test` WHERE field2 = ?"
    "Statement"    | Select.select("field1").from("test").where(Expression.path("field2").eq(Expression.s("asdf"))) | "SELECT field1 FROM test WHERE field2 = ?"
    "N1QL"         | N1qlQuery.simple("SELECT field1 FROM `test` WHERE field2 = 'asdf'")                            | "SELECT field1 FROM `test` WHERE field2 = ?"
    "Analytics"    | AnalyticsQuery.simple("SELECT field1 FROM `test` WHERE field2 = 'asdf'")                       | "SELECT field1 FROM `test` WHERE field2 = ?"
    "View"         | ViewQuery.from("design", "view").skip(10)                                                      | 'ViewQuery(design/view){params="skip=10"}'
    "SpatialView"  | SpatialViewQuery.from("design", "view").skip(10)                                               | 'skip=10'
  }
}
