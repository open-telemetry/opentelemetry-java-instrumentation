/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.datastax.oss.driver.api.core.CqlSession
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.cassandra.AbstractCassandraClientTest
import reactor.core.publisher.Flux

class CassandraClientTest extends AbstractCassandraClientTest {

  def "test reactive"() {
    setup:
    CqlSession session = getSession(keyspace)

    runWithSpan("parent") {
      Flux.from(session.executeReactive(statement)).doOnComplete({ result ->
        runWithSpan("child") {}
      }).blockLast()
    }

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        cassandraSpan(it, 1, spanName, expectedStatement, operation, keyspace, table, span(0))
        span(2) {
          name "child"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    session.close()

    where:
    keyspace        | statement                                                                                              | expectedStatement                                                     | spanName                      | operation | table
    null            | "DROP KEYSPACE IF EXISTS reactive_test"                                                                | "DROP KEYSPACE IF EXISTS reactive_test"                               | "DB Query"                    | null      | null
    null            | "CREATE KEYSPACE reactive_test WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}"  | "CREATE KEYSPACE reactive_test WITH REPLICATION = {?:?, ?:?}"         | "DB Query"                    | null      | null
    "reactive_test" | "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )"                                  | "CREATE TABLE reactive_test.users ( id UUID PRIMARY KEY, name text )" | "reactive_test"               | null      | null
    "reactive_test" | "INSERT INTO reactive_test.users (id, name) values (uuid(), 'alice')"                                  | "INSERT INTO reactive_test.users (id, name) values (uuid(), ?)"       | "INSERT reactive_test.users"  | "INSERT"  | "reactive_test.users"
    "reactive_test" | "SELECT * FROM users where name = 'alice' ALLOW FILTERING"                                             | "SELECT * FROM users where name = ? ALLOW FILTERING"                  | "SELECT reactive_test.users"  | "SELECT"  | "users"
  }

}
