/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


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

    expect:
    assertTraces(sessionImplementations.size()) {
      for (int i = 0; i < sessionImplementations.size(); i++) {
        trace(i, 4) {
          span(0) {
            name "Session"
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
            }
          }
          span(2) {
            name "SELECT db1.Value"
            kind CLIENT
            childOf span(1)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "h2"
              "${SemanticAttributes.DB_NAME.key}" "db1"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
              "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
              "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
            }
          }
          span(3) {
            name "Transaction.commit"
            kind INTERNAL
            childOf span(0)
            attributes {
            }
          }
        }
      }
    }

    where:
    testName  | methodName | resource | sessionImplementations                    | sessionMethodTest
    "lock"    | "lock"     | "Value"  | [sessionBuilder]                          | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh" | "refresh"  | "Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.refresh(val)
    }
    "get"     | "get"      | "Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.get("Value", val.getId())
    }
  }

  def "test hibernate statless action #testName"() {
    setup:

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

    expect:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "Session"
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
          }
        }
        span(2) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(3) {
          kind CLIENT
          childOf span(2)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" String
            "${SemanticAttributes.DB_OPERATION.key}" String
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
      }
    }

    where:
    testName               | methodName | resource | sessionMethodTest
    "insert"               | "insert"   | "Value"  | { sesh, val ->
      sesh.insert("Value", new Value("insert me"))
    }
    "update"               | "update"   | "Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName" | "update"   | "Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "delete"               | "delete"   | "Value"  | { sesh, val ->
      sesh.delete(val)
    }
  }

  def "test hibernate replicate: #testName"() {
    setup:

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

    expect:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "Session"
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
          }
        }
        span(2) {
          name "SELECT db1.Value"
          kind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(4) {
          kind CLIENT
          childOf span(3)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" String
            "${SemanticAttributes.DB_OPERATION.key}" String
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
      }

    }

    where:
    testName                  | methodName  | resource | sessionMethodTest
    "replicate"               | "replicate" | "Value"  | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate by entityName" | "replicate" | "Value"  | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate("Value", replicated, ReplicationMode.OVERWRITE)
    }
  }

  def "test hibernate failed replicate"() {
    setup:

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

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "Session"
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
        }
        span(2) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }

    }
  }


  def "test hibernate commit action #testName"() {
    setup:

    def session = sessionBuilder()
    session.beginTransaction()

    try {
      sessionMethodTest.call(session, prepopulated.get(0))
    } catch (Exception e) {
      // We expected this, we should see the error field set on the span.
    }

    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "Session"
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
          }
        }
        span(2) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(3) {
          kind CLIENT
          childOf span(2)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" String
            "${SemanticAttributes.DB_OPERATION.key}" String
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
      }
    }

    where:
    testName                         | methodName     | resource | sessionMethodTest
    "save"                           | "save"         | "Value"  | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
    "saveOrUpdate save"              | "saveOrUpdate" | "Value"  | { sesh, val ->
      sesh.saveOrUpdate(new Value("Value"))
    }
    "saveOrUpdate update"            | "saveOrUpdate" | "Value"  | { sesh, val ->
      val.setName("New name")
      sesh.saveOrUpdate(val)
    }
    "merge"                          | "merge"        | "Value"  | { sesh, val ->
      sesh.merge(new Value("merge me in"))
    }
    "persist"                        | "persist"      | "Value"  | { sesh, val ->
      sesh.persist(new Value("merge me in"))
    }
    "update (Session)"               | "update"       | "Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName (Session)" | "update"       | "Value"  | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "delete (Session)"               | "delete"       | "Value"  | { sesh, val ->
      sesh.delete(val)
    }
  }


  def "test attaches State to query created via #queryMethodName"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Query query = queryBuildMethod(session)
    query.list()
    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "Session"
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
          }
        }
        span(2) {
          kind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" String
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }

    where:
    queryMethodName  | expectedSpanName | queryBuildMethod
    "createQuery"    | "SELECT Value"   | { sess -> sess.createQuery("from Value") }
    "getNamedQuery"  | "SELECT Value"   | { sess -> sess.getNamedQuery("TestNamedQuery") }
    "createSQLQuery" | "SELECT Value"   | { sess -> sess.createSQLQuery("SELECT * FROM Value") }
  }


  def "test hibernate overlapping Sessions"() {
    setup:

    runWithSpan("overlapping Sessions") {
      def session1 = sessionFactory.openSession()
      session1.beginTransaction()
      def session2 = sessionFactory.openStatelessSession()
      def session3 = sessionFactory.openSession()

      def value1 = new Value("Value 1")
      session1.save(value1)
      session2.insert(new Value("Value 2"))
      session3.save(new Value("Value 3"))
      session1.delete(value1)

      session2.close()
      session1.getTransaction().commit()
      session1.close()
      session3.close()
    }

    expect:
    assertTraces(1) {
      trace(0, 11) {
        span(0) {
          name "overlapping Sessions"
          attributes {
          }
        }
        span(1) {
          name "Session"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "Session.save Value"
          kind INTERNAL
          childOf span(1)
          attributes {
          }
        }
        span(3) {
          name "Session.delete Value"
          kind INTERNAL
          childOf span(1)
          attributes {
          }
        }
        span(4) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(1)
          attributes {
          }
        }
        span(5) {
          name "INSERT db1.Value"
          kind CLIENT
          childOf span(4)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^insert /
            "${SemanticAttributes.DB_OPERATION.key}" "INSERT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
        span(6) {
          name "DELETE db1.Value"
          kind CLIENT
          childOf span(4)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "h2"
            "${SemanticAttributes.DB_NAME.key}" "db1"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "h2:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^delete /
            "${SemanticAttributes.DB_OPERATION.key}" "DELETE"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Value"
          }
        }
        span(7) {
          name "Session"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(8) {
          name "Session.insert Value"
          kind INTERNAL
          childOf span(7)
          attributes {
          }
        }
        span(9) {
          name "Session"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(10) {
          name "Session.save Value"
          kind INTERNAL
          childOf span(9)
          attributes {
          }
        }
      }
    }
  }
}

