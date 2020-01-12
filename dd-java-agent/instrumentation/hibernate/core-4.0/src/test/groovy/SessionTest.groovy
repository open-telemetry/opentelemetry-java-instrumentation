import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.AgentScope
import io.opentelemetry.auto.instrumentation.api.Tags
import org.hibernate.LockMode
import org.hibernate.MappingException
import org.hibernate.Query
import org.hibernate.ReplicationMode
import org.hibernate.Session
import spock.lang.Shared

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan

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
            operationName "hibernate.session"
            parent()
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
              "$Tags.COMPONENT" "java-hibernate"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            }
          }
          span(1) {
            operationName "hibernate.$methodName"
            childOf span(0)
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$MoreTags.RESOURCE_NAME" resource
              "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
              "$Tags.COMPONENT" "java-hibernate"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            }
          }
          span(2) {
            childOf span(1)
            tags {
              "$MoreTags.SERVICE_NAME" "h2"
              "$MoreTags.RESOURCE_NAME" String
              "$MoreTags.SPAN_TYPE" "sql"
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
              "$Tags.DB_TYPE" "h2"
              "$Tags.DB_INSTANCE" "db1"
              "$Tags.DB_USER" "sa"
              "$Tags.DB_STATEMENT" String
              "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
            }
          }
          span(3) {
            operationName "hibernate.transaction.commit"
            childOf span(0)
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
              "$Tags.COMPONENT" "java-hibernate"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            }
          }
        }
      }
    }

    where:
    testName                                  | methodName | resource | sessionImplementations                    | sessionMethodTest
    "lock"                                    | "lock"     | "Value"  | [sessionBuilder]                          | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh"                                 | "refresh"  | "Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.refresh(val)
    }
    "get"                                     | "get"      | "Value"  | [sessionBuilder, statelessSessionBuilder] | { sesh, val ->
      sesh.get("Value", val.getId())
    }
    "insert"                                  | "insert"   | "Value"  | [statelessSessionBuilder]                 | { sesh, val ->
      sesh.insert("Value", new Value("insert me"))
    }
    "update (StatelessSession)"               | "update"   | "Value"  | [statelessSessionBuilder]                 | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName (StatelessSession)" | "update"   | "Value"  | [statelessSessionBuilder]                 | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "delete (Session)"                        | "delete"   | "Value"  | [statelessSessionBuilder]                 | { sesh, val ->
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
          operationName "hibernate.session"
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) {
          operationName "hibernate.$methodName"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" resource
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(2) {
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "hibernate.transaction.commit"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(4) {
          childOf span(3)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" String
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" String
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
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
          operationName "hibernate.session"
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) {
          operationName "hibernate.replicate"
          childOf span(0)
          errored(true)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(MappingException, "Unknown entity: java.lang.Long")
          }
        }
        span(2) {
          operationName "hibernate.transaction.commit"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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
          operationName "hibernate.session"
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) {
          operationName "hibernate.$methodName"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" resource
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(2) {
          operationName "hibernate.transaction.commit"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(3) {
          childOf span(2)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" String
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" String
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
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
          operationName "hibernate.session"
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) {
          operationName "hibernate.query.list"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "$resource"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_STATEMENT" String
          }
        }
        span(2) {
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" String
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" String
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "hibernate.transaction.commit"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
      }
    }

    where:
    queryMethodName  | resource              | queryBuildMethod
    "createQuery"    | "Value"               | { sess -> sess.createQuery("from Value") }
    "getNamedQuery"  | "Value"               | { sess -> sess.getNamedQuery("TestNamedQuery") }
    "createSQLQuery" | "SELECT * FROM Value" | { sess -> sess.createSQLQuery("SELECT * FROM Value") }
  }


  def "test hibernate overlapping Sessions"() {
    setup:

    AgentScope scope = activateSpan(startSpan("overlapping Sessions"), true)

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

    scope.close()


    expect:
    assertTraces(1) {
      trace(0, 12) {
        span(0) {
          operationName "overlapping Sessions"
          tags {
          }
        }
        span(1) {
          operationName "hibernate.session"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(2) {
          operationName "hibernate.save"
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "Value"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(3) {
          operationName "hibernate.delete"
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "Value"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(4) {
          operationName "hibernate.transaction.commit"
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(5) {
          childOf span(4)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" ~/^insert /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^insert /
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(6) {
          childOf span(4)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" ~/^delete /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^delete /
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(7) {
          operationName "hibernate.session"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(8) {
          operationName "hibernate.insert"
          childOf span(7)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "Value"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(9) {
          childOf span(8)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" ~/^insert /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^insert /
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(10) {
          operationName "hibernate.session"
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(11) {
          operationName "hibernate.save"
          childOf span(10)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "Value"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
      }
    }
  }
}

