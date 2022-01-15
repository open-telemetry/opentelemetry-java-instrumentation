/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared
import spock.lang.Unroll

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.Persistence
import javax.persistence.Query

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class EntityManagerTest extends AbstractHibernateTest {

  @Shared
  EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("test-pu")

  @Unroll
  def "test hibernate action #testName"() {
    setup:
    EntityManager entityManager = entityManagerFactory.createEntityManager()
    EntityTransaction entityTransaction = entityManager.getTransaction()
    entityTransaction.begin()

    def entity = prepopulated.get(0)
    if (attach) {
      entity = runWithSpan("setup") {
        entityManager.merge(prepopulated.get(0))
      }
      ignoreTracesAndClear(1)
    }

    when:
    runWithSpan("parent") {
      try {
        sessionMethodTest.call(entityManager, entity)
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
      }

      entityTransaction.commit()
      entityManager.close()
    }

    then:
    boolean isPersistTest = "persist" == testName
    def sessionId
    assertTraces(1) {
      trace(0, 4 + (isPersistTest ? 1 : 0)) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name ~/Session.$methodName $resource/
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }

        def offset = 0
        if (isPersistTest) {
          // persist test has an extra query for getting id of inserted element
          offset = 1
          span(2) {
            name "SELECT db1.Value"
            childOf span(1)
            kind CLIENT
            attributes {
              "$SemanticAttributes.DB_SYSTEM" "h2"
              "$SemanticAttributes.DB_NAME" "db1"
              "$SemanticAttributes.DB_USER" "sa"
              "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
              "$SemanticAttributes.DB_STATEMENT" String
              "$SemanticAttributes.DB_OPERATION" String
              "$SemanticAttributes.DB_SQL_TABLE" "Value"
            }
          }
        }

        if (!flushOnCommit) {
          span(2 + offset) {
            childOf span(1)
            kind CLIENT
            attributes {
              "$SemanticAttributes.DB_SYSTEM" "h2"
              "$SemanticAttributes.DB_NAME" "db1"
              "$SemanticAttributes.DB_USER" "sa"
              "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
              "$SemanticAttributes.DB_STATEMENT" String
              "$SemanticAttributes.DB_OPERATION" String
              "$SemanticAttributes.DB_SQL_TABLE" "Value"
            }
          }
          span(3 + offset) {
            name "Transaction.commit"
            kind INTERNAL
            childOf span(0)
            attributes {
              "hibernate.session_id" sessionId
            }
          }
        } else {
          span(2 + offset) {
            name "Transaction.commit"
            kind INTERNAL
            childOf span(0)
            attributes {
              "hibernate.session_id" sessionId
            }
          }
          span(3 + offset) {
            childOf span(2 + offset)
            kind CLIENT
            attributes {
              "$SemanticAttributes.DB_SYSTEM" "h2"
              "$SemanticAttributes.DB_NAME" "db1"
              "$SemanticAttributes.DB_USER" "sa"
              "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
              "$SemanticAttributes.DB_STATEMENT" String
              "$SemanticAttributes.DB_OPERATION" String
              "$SemanticAttributes.DB_SQL_TABLE" "Value"
            }
          }
        }
      }
    }

    where:
    testName  | methodName   | resource | attach | flushOnCommit | sessionMethodTest
    "lock"    | "lock"       | "Value"  | true   | false         | { em, val ->
      em.lock(val, LockModeType.PESSIMISTIC_READ)
    }
    "refresh" | "refresh"    | "Value"  | true   | false         | { em, val ->
      em.refresh(val)
    }
    "find"    | "(get|find)" | "Value"  | false  | false         | { em, val ->
      em.find(Value, val.getId())
    }
    "persist" | "persist"    | "Value"  | false  | true          | { em, val ->
      em.persist(new Value("insert me"))
    }
    "merge"   | "merge"      | "Value"  | true   | true          | { em, val ->
      val.setName("New name")
      em.merge(val)
    }
    "remove"  | "delete"     | "Value"  | true   | true          | { em, val ->
      em.remove(val)
    }
  }

  @Unroll
  def "test attaches State to query created via #queryMethodName"() {
    setup:
    runWithSpan("parent") {
      EntityManager entityManager = entityManagerFactory.createEntityManager()
      EntityTransaction entityTransaction = entityManager.getTransaction()
      entityTransaction.begin()
      Query query = queryBuildMethod(entityManager)
      query.getResultList()
      entityTransaction.commit()
      entityManager.close()
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
          name resource
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
            "$SemanticAttributes.DB_STATEMENT" String
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
    queryMethodName  | resource       | queryBuildMethod
    "createQuery"    | "SELECT Value" | { em -> em.createQuery("from Value") }
    "getNamedQuery"  | "SELECT Value" | { em -> em.createNamedQuery("TestNamedQuery") }
    "createSQLQuery" | "SELECT Value" | { em -> em.createNativeQuery("SELECT * FROM Value") }
  }

}
