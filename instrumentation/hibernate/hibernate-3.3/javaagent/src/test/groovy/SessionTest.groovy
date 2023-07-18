/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.hibernate.LockMode
import org.hibernate.MappingException
import org.hibernate.Query
import org.hibernate.ReplicationMode
import org.hibernate.Session
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR

class SessionTest extends AbstractHibernateTest {

  @Shared
  private Closure sessionBuilder = { return sessionFactory.openSession() }
  @Shared
  private Closure statelessSessionBuilder = { return sessionFactory.openStatelessSession() }


  def "test hibernate action #testName"() {
    setup:

    // Test for each implementation of Session.
    for (def buildSession : sessionImplementations) {
      runWithSpan("parent") {
        def session = buildSession()
        session.beginTransaction()

        try {
          sessionMethodTest.call(session, prepopulated.get(0))
        } catch (Exception e) {
          // We expected this, we should see the error field set on the span.
        }

        session.getTransaction().commit()
        session.close()
      }
    }

    expect:
    def sessionId
    assertTraces(sessionImplementations.size()) {
      for (int i = 0; i < sessionImplementations.size(); i++) {
        trace(i, 4) {
          span(0) {
            name "parent"
            kind INTERNAL
            hasNoParent()
            attributes {
            }
          }
          span(1) {
            name "Session.$methodName $resource"
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

    where:
    testName  | methodName | resource | sessionImplementations                    | sessionMethodTest
    "lock"    | "lock"     | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | [sessionBuilder]                          | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh" | "refresh"  | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.refresh(val)
    }
    "get"     | "get"      | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.get("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val.getId())
    }
  }

  def "test hibernate statless action #testName"() {
    setup:

    runWithSpan("parent") {
      // Test for each implementation of Session.
      def session = statelessSessionBuilder()
      session.beginTransaction()

      try {
        sessionMethodTest.call(session, prepopulated.get(0))
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
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
          name "Session.$methodName $resource"
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
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
        span(3) {
          kind CLIENT
          childOf span(2)
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
      }
    }

    where:
    testName               | methodName | resource | sessionMethodTest
    "insert"               | "insert"   | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.insert("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", new Value("insert me"))
    }
    "update"               | "update"   | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName" | "update"   | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val)
    }
    "delete"               | "delete"   | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.delete(val)
    }
  }

  def "test hibernate replicate: #testName"() {
    setup:

    runWithSpan("parent") {
      // Test for each implementation of Session.
      def session = sessionFactory.openSession()
      session.beginTransaction()

      try {
        sessionMethodTest.call(session, prepopulated.get(0))
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
      }

      session.getTransaction().commit()
      session.close()
    }

    expect:
    def sessionId
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "Session.$methodName $resource"
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
        span(4) {
          kind CLIENT
          childOf span(3)
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
      }

    }

    where:
    testName                  | methodName  | resource | sessionMethodTest
    "replicate"               | "replicate" | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate by entityName" | "replicate" | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", replicated, ReplicationMode.OVERWRITE)
    }
  }

  def "test hibernate failed replicate"() {
    setup:

    runWithSpan("parent") {
      // Test for each implementation of Session.
      def session = sessionFactory.openSession()
      session.beginTransaction()

      try {
        session.replicate(new Long(123) /* Not a valid entity */, ReplicationMode.OVERWRITE)
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
      }

      session.getTransaction().commit()
      session.close()
    }

    expect:
    def sessionId
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "Session.replicate java.lang.Long"
          kind INTERNAL
          childOf span(0)
          status ERROR
          errorEvent(MappingException, "Unknown entity: java.lang.Long")
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }
        span(2) {
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


  def "test hibernate commit action #testName"() {
    setup:

    runWithSpan("parent") {
      def session = sessionBuilder()
      session.beginTransaction()

      try {
        sessionMethodTest.call(session, prepopulated.get(0))
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
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
          name "Session.$methodName $resource"
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
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
        span(3) {
          kind CLIENT
          childOf span(2)
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
      }
    }

    where:
    testName                         | methodName     | resource | sessionMethodTest
    "save"                           | "save"         | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
    "saveOrUpdate save"              | "saveOrUpdate" | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.saveOrUpdate(new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"))
    }
    "saveOrUpdate update"            | "saveOrUpdate" | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      val.setName("New name")
      sesh.saveOrUpdate(val)
    }
    "merge"                          | "merge"        | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.merge(new Value("merge me in"))
    }
    "persist"                        | "persist"      | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.persist(new Value("merge me in"))
    }
    "update (Session)"               | "update"       | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName (Session)" | "update"       | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val)
    }
    "delete (Session)"               | "delete"       | "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"  | { sesh, val ->
      sesh.delete(val)
    }
  }


  def "test attaches State to query created via #queryMethodName"() {
    setup:
    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()
      Query query = queryBuildMethod(session)
      query.list()
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

    where:
    queryMethodName  | expectedSpanName | queryBuildMethod
    "createQuery"    | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | { sess -> sess.createQuery("from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value") }
    "getNamedQuery"  | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | { sess -> sess.getNamedQuery("TestNamedQuery") }
    "createSQLQuery" | "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"   | { sess -> sess.createSQLQuery("SELECT * FROM io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value") }
  }


  def "test hibernate overlapping Sessions"() {
    setup:

    runWithSpan("overlapping Sessions") {
      def session1 = sessionFactory.openSession()
      session1.beginTransaction()
      def session2 = sessionFactory.openStatelessSession()
      def session3 = sessionFactory.openSession()

      def value1 = new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 1")
      session1.save(value1)
      session2.insert(new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 2"))
      session3.save(new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 3"))
      session1.delete(value1)

      session2.close()
      session1.getTransaction().commit()
      session1.close()
      session3.close()
    }

    expect:
    def sessionId1
    def sessionId2
    def sessionId3
    assertTraces(1) {
      trace(0, 8) {
        span(0) {
          name "overlapping Sessions"
          attributes {
          }
        }
        span(1) {
          name "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId1 = it
              it instanceof String
            }
          }
        }
        span(2) {
          name "Session.insert io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId2 = it
              it instanceof String
            }
          }
        }
        span(3) {
          name "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId3 = it
              it instanceof String
            }
          }
        }
        span(4) {
          name "Session.delete io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId1
          }
        }
        span(5) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId1
          }
        }
        span(6) {
          name "INSERT db1.io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind CLIENT
          childOf span(5)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" "db1"
            "$SemanticAttributes.DB_USER" "sa"
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" ~/^insert /
            "$SemanticAttributes.DB_OPERATION" "INSERT"
            "$SemanticAttributes.DB_SQL_TABLE" "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          }
        }
        span(7) {
          name "DELETE db1.io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          kind CLIENT
          childOf span(5)
          attributes {
            "$SemanticAttributes.DB_SYSTEM" "h2"
            "$SemanticAttributes.DB_NAME" "db1"
            "$SemanticAttributes.DB_USER" "sa"
            "$SemanticAttributes.DB_CONNECTION_STRING" "h2:mem:"
            "$SemanticAttributes.DB_STATEMENT" ~/^delete /
            "$SemanticAttributes.DB_OPERATION" "DELETE"
            "$SemanticAttributes.DB_SQL_TABLE" "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"
          }
        }
      }
    }
    sessionId1 != sessionId2 != sessionId3
  }
}
