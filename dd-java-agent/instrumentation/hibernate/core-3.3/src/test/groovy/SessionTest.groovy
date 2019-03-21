import datadog.trace.api.DDSpanTypes
import io.opentracing.Scope
import io.opentracing.Tracer
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.hibernate.*
import spock.lang.Shared

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
            serviceName "hibernate"
            resourceName "hibernate.session"
            operationName "hibernate.session"
            spanType DDSpanTypes.HIBERNATE
            parent()
            tags {
              "$Tags.COMPONENT.key" "java-hibernate"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(1) {
            serviceName "hibernate"
            resourceName "hibernate.transaction.commit"
            operationName "hibernate.transaction.commit"
            spanType DDSpanTypes.HIBERNATE
            childOf span(0)
            tags {
              "$Tags.COMPONENT.key" "java-hibernate"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(2) {
            serviceName "hibernate"
            resourceName resource
            operationName "hibernate.$methodName"
            spanType DDSpanTypes.HIBERNATE
            childOf span(0)
            tags {
              "$Tags.COMPONENT.key" "java-hibernate"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(3) {
            serviceName "h2"
            spanType "sql"
            childOf span(2)
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
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "h2"
          spanType "sql"
          childOf span(1)
        }
        span(3) {
          serviceName "hibernate"
          resourceName resource
          operationName "hibernate.$methodName"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
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
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "h2"
          spanType "sql"
          childOf span(1)
        }
        span(3) {
          serviceName "hibernate"
          resourceName resource
          operationName "hibernate.$methodName"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(4) {
          serviceName "h2"
          spanType "sql"
          childOf span(3)
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
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "hibernate.replicate"
          operationName "hibernate.replicate"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          errored(true)
          tags {
            errorTags(MappingException, "Unknown entity: java.lang.Long")

            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
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
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "h2"
          spanType "sql"
          childOf span(1)
        }
        span(3) {
          serviceName "hibernate"
          resourceName resource
          operationName "hibernate.$methodName"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
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
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "$resource"
          operationName "hibernate.query.list"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(3) {
          serviceName "h2"
          spanType "sql"
          childOf span(2)
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

    Tracer tracer = GlobalTracer.get()

    Scope scope = tracer.buildSpan("overlapping Sessions").startActive(true)

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
      trace(0, 11) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "overlapping Sessions"
        }
        span(1) {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(3) {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(2)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(4) {
          serviceName "h2"
          spanType "sql"
          childOf span(3)
        }
        span(5) {
          serviceName "h2"
          spanType "sql"
          childOf span(3)
        }
        span(6) {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(7) {
          serviceName "hibernate"
          resourceName "Value"
          operationName "hibernate.delete"
          spanType DDSpanTypes.HIBERNATE
          childOf span(2)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(8) {
          serviceName "hibernate"
          resourceName "Value"
          operationName "hibernate.save"
          spanType DDSpanTypes.HIBERNATE
          childOf span(1)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(9) {
          serviceName "hibernate"
          resourceName "Value"
          operationName "hibernate.insert"
          spanType DDSpanTypes.HIBERNATE
          childOf span(6)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(10) {
          serviceName "hibernate"
          resourceName "Value"
          operationName "hibernate.save"
          spanType DDSpanTypes.HIBERNATE
          childOf span(2)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }
}

