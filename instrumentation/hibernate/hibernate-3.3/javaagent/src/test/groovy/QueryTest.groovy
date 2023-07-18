/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.hibernate.Query
import org.hibernate.Session

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class QueryTest extends AbstractHibernateTest {

  def "test hibernate query.#queryMethodName single call"() {
    setup:

    // With Transaction
    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()
      queryInteraction(session)
      session.getTransaction().commit()
      session.close()
    }

    // Without Transaction
    if (!requiresTransaction) {
      runWithSpan("parent2") {
        Session session = sessionFactory.openSession()
        queryInteraction(session)
        session.close()
      }
    }

    expect:
    def sessionId
    assertTraces(requiresTransaction ? 1 : 2) {
      // With Transaction
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name expectedSpanName
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }
        span(2) {
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" "db1"
            "$SemanticAttributes.DB_USER" "sa"
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" String
            "$SemanticAttributes.DB_OPERATION" String
            "$SemanticAttributes.DB_SQL_TABLE" "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
      }
      if (!requiresTransaction) {
        // Without Transaction
        trace(1, 3) {
          span(0) {
            name "parent2"
            kind INTERNAL
            hasNoParent()
            attributes {
            }
          }
          span(1) {
            name expectedSpanName
            kind INTERNAL
            childOf span(0)
            attributes {
              "hibernate.session_id" String
            }
          }
          span(2) {
            name "SELECT db1.io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
            kind CLIENT
            childOf span(1)
            attributes {
              "$SemanticAttributes.DB_SYSTEM" "h2"
              "$SemanticAttributes.DB_NAME" "db1"
              "$SemanticAttributes.DB_USER" "sa"
              "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
              "$SemanticAttributes.DB_STATEMENT" ~/^select /
              "$SemanticAttributes.DB_OPERATION" "SELECT"
              "$SemanticAttributes.DB_SQL_TABLE" "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
            }
          }
        }
      }
    }

    where:
    queryMethodName       | expectedSpanName | requiresTransaction | queryInteraction
    "Query.list"          | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | false               | { sess ->
      Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")
      q.list()
    }
    "Query.executeUpdate" | "UPDATE io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | true                | { sess ->
      Query q = sess.createQuery("update io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value set name = ?")
      q.setParameter(0, "alyx")
      q.executeUpdate()
    }
    "Query.uniqueResult"  | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | false               | { sess ->
      Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value where id = ?")
      q.setParameter(0, 1L)
      q.uniqueResult()
    }
    "Query.iterate"       | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | false               | { sess ->
      Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")
      q.iterate()
    }
    "Query.scroll"        | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | false               | { sess ->
      Query q = sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")
      q.scroll()
    }
  }

  def "test hibernate query.iterate"() {
    setup:

    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()
      Query q = session.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")
      Iterator iterator = q.iterate()
      while (iterator.hasNext()) {
        iterator.next()
      }
      session.getTransaction().commit()
      session.close()
    }

    expect:
    def sessionId
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }
        span(2) {
          name "SELECT db1.io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" "db1"
            "$SemanticAttributes.DB_USER" "sa"
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" ~/^select /
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
      }
    }
  }

}
