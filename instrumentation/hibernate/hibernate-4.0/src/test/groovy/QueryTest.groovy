/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import org.hibernate.Query
import org.hibernate.Session

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

class QueryTest extends AbstractHibernateTest {

  def "test hibernate query.#queryMethodName single call"() {
    setup:

    // With Transaction
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    queryInteraction(session)
    session.getTransaction().commit()
    session.close()

    // Without Transaction
    if (!requiresTransaction) {
      session = sessionFactory.openSession()
      queryInteraction(session)
      session.close()
    }

    expect:
    assertTraces(requiresTransaction ? 1 : 2) {
      // With Transaction
      trace(0, 4) {
        span(0) {
          operationName "Session"
          spanKind INTERNAL
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(1) {
          operationName expectedSpanName
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(2) {
          spanKind CLIENT
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" String
            "$Tags.DB_URL" "h2:mem:"
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "Transaction.commit"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
      }
      if (!requiresTransaction) {
        // Without Transaction
        trace(1, 3) {
          span(0) {
            operationName "Session"
            spanKind INTERNAL
            parent()
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$Tags.COMPONENT" "java-hibernate"
            }
          }
          span(1) {
            operationName expectedSpanName
            spanKind INTERNAL
            childOf span(0)
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$Tags.COMPONENT" "java-hibernate"
            }
          }
          span(2) {
            operationName ~/^select /
            spanKind CLIENT
            childOf span(1)
            tags {
              "$MoreTags.SERVICE_NAME" "h2"
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
              "$Tags.DB_TYPE" "sql"
              "$Tags.DB_INSTANCE" "db1"
              "$Tags.DB_USER" "sa"
              "$Tags.DB_STATEMENT" ~/^select /
              "$Tags.DB_URL" "h2:mem:"
              "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
            }
          }
        }
      }
    }

    where:
    queryMethodName       | expectedSpanName            | requiresTransaction | queryInteraction
    "query/list"          | "from Value"                | false               | { sess ->
      Query q = sess.createQuery("from Value")
      q.list()
    }
    "query/executeUpdate" | "update Value set name = ?" | true                | { sess ->
      Query q = sess.createQuery("update Value set name = ?")
      q.setParameter(0, "alyx")
      q.executeUpdate()
    }
    "query/uniqueResult"  | "from Value where id = ?"   | false               | { sess ->
      Query q = sess.createQuery("from Value where id = ?")
      q.setParameter(0, 1L)
      q.uniqueResult()
    }
    "iterate"             | "from Value"                | false               | { sess ->
      Query q = sess.createQuery("from Value")
      q.iterate()
    }
    "query/scroll"        | "from Value"                | false               | { sess ->
      Query q = sess.createQuery("from Value")
      q.scroll()
    }
  }

  def "test hibernate query.iterate"() {
    setup:

    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Query q = session.createQuery("from Value")
    Iterator it = q.iterate()
    while (it.hasNext()) {
      it.next()
    }
    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "Session"
          spanKind INTERNAL
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(1) {
          operationName "from Value"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(2) {
          operationName ~/^select /
          spanKind CLIENT
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "$Tags.DB_URL" "h2:mem:"
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "Transaction.commit"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
      }
    }
  }

}
