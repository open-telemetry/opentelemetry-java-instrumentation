/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.hibernate.Session

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class CriteriaTest extends AbstractHibernateTest {

  def "test criteria query.#methodName"() {
    setup:
    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()
      CriteriaBuilder criteriaBuilder = session.getCriteriaBuilder()
      CriteriaQuery<Value> createQuery = criteriaBuilder.createQuery(Value)
      Root<Value> root = createQuery.from(Value)
      createQuery.select(root)
        .where(criteriaBuilder.like(root.get("name"), "Hello"))
        .orderBy(criteriaBuilder.desc(root.get("name")))
      def query= session.createQuery(createQuery)
      interaction.call(query)
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
          name "SELECT Value"
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
          name "SELECT db1.Value"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" "db1"
            "$SemanticAttributes.DB_USER" "sa"
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" ~/^select /
            "$SemanticAttributes.DB_OPERATION" "SELECT"
            "$SemanticAttributes.DB_SQL_TABLE" "Value"
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

    where:
    methodName              | interaction
    "getResultList"         | { c -> c.getResultList() }
    "uniqueResult"          | { c -> c.uniqueResult() }
    "getSingleResultOrNull" | { c -> c.getSingleResultOrNull() }
  }
}
