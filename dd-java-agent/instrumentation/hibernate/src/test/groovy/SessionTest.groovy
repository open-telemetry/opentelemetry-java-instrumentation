import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.hibernate.*
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import spock.lang.Shared

class SessionTest extends AgentTestRunner {

  @Shared
  private SessionFactory sessionFactory

  @Shared
  private Map<String, Closure> sessionBuilders

  @Shared
  private List<Value> prepopulated

  def setupSpec() {
    final StandardServiceRegistry registry =
      new StandardServiceRegistryBuilder()
        .configure()
        .build()
    try {
      sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
    } catch (Exception e) {
      StandardServiceRegistryBuilder.destroy(registry)
    }

    // Test two different types of Session. Groovy doesn't allow testing the cross-product/combinations of two data
    // tables, so we get this hack instead.
    sessionBuilders = new HashMap<>();
    sessionBuilders.put("Session", { return sessionFactory.openSession() })
    sessionBuilders.put("StatelessSession", { return sessionFactory.openStatelessSession() })

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession()
    writer.beginTransaction()
    prepopulated = new ArrayList<>()
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :)"))
      writer.save(prepopulated.get(i))
    }
    writer.getTransaction().commit()
    writer.close()
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }


  def "test hibernate action #testName"() {
    setup:

    // Test for each implementation of Session.
    for (String sessionImplementation : sessionImplementations) {
      def session = sessionBuilders.get(sessionImplementation)()
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
              "$Tags.COMPONENT.key" "hibernate-java"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
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
              "$Tags.COMPONENT.key" "hibernate-java"
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
              defaultTags()
            }
          }
          span(2) {
            serviceName "hibernate"
            resourceName resource
            operationName "hibernate.$methodName"
            spanType DDSpanTypes.HIBERNATE
            childOf span(0)
            if (isError) {
              errored true
            }
            tags {
              if (isError) {
                errorTags(MappingException, "Unknown entity: java.lang.Long")
              }
              "$Tags.COMPONENT.key" "hibernate-java"
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
              defaultTags()
            }
          }
          span(3) {
            serviceName "h2"
            childOf span(2)
          }
        }
      }
    }

    where:
    testName                                  | methodName | resource | isError | sessionImplementations          | sessionMethodTest
    "lock"                                    | "lock"     | "Value"  | false   | ["Session"]                     | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh"                                 | "refresh"  | "Value"  | false   | ["Session", "StatelessSession"] | { sesh, val ->
      sesh.refresh(val)
    }
    "get"                                     | "get"      | "Value"  | false   | ["Session", "StatelessSession"] | { sesh, val ->
      sesh.get("Value", val.getId())
    }
    "insert"                                  | "insert"   | "Value"  | false   | ["StatelessSession"]            | { sesh, val ->
      sesh.insert("Value", new Value("insert me"))
    }
    "update (StatelessSession)"               | "update"   | "Value"  | false   | ["StatelessSession"]            | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName (StatelessSession)" | "update"   | "Value"  | false   | ["StatelessSession"]            | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "delete (Session)"                        | "delete"   | "Value"  | false   | ["StatelessSession"]            | { sesh, val ->
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(2) {
          serviceName "h2"
          childOf span(1)
        }
        span(3) {
          serviceName "hibernate"
          resourceName resource
          operationName "hibernate.$methodName"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          if (isError) {
            errored true
          }
          tags {
            if (isError) {
              errorTags(MappingException, "Unknown entity: java.lang.Long")
            }
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(4) {
          serviceName "h2"
          childOf span(3)
        }
      }

    }

    where:
    testName                  | methodName  | resource | isError | sessionMethodTest
    "replicate"               | "replicate" | "Value"  | false   | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate by entityName" | "replicate" | "Value"  | false   | { sesh, val ->
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "unknown object"
          operationName "hibernate.replicate"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            errorTags(MappingException, "Unknown entity: java.lang.Long")

            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
      }

    }
  }


  def "test hibernate commit action #testName"() {
    setup:

    // Test for each implementation of Session.
    for (String sessionImplementation : sessionImplementations) {
      def session = sessionBuilders.get(sessionImplementation)()
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
              "$Tags.COMPONENT.key" "hibernate-java"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
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
              "$Tags.COMPONENT.key" "hibernate-java"
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
              defaultTags()
            }
          }
          span(2) {
            serviceName "h2"
            childOf span(1)
          }
          span(3) {
            serviceName "hibernate"
            resourceName resource
            operationName "hibernate.$methodName"
            spanType DDSpanTypes.HIBERNATE
            childOf span(0)
            if (isError) {
              errored true
            }
            tags {
              if (isError) {
                errorTags(MappingException, "Unknown entity: java.lang.Long")
              }
              "$Tags.COMPONENT.key" "hibernate-java"
              "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
              defaultTags()
            }
          }
        }
      }
    }

    where:
    testName                         | methodName     | resource | isError | sessionImplementations | sessionMethodTest
    "save"                           | "save"         | "Value"  | false   | ["Session"]            | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
    "saveOrUpdate save"              | "saveOrUpdate" | "Value"  | false   | ["Session"]            | { sesh, val ->
      sesh.saveOrUpdate(new Value("Value"))
    }
    "saveOrUpdate update"            | "saveOrUpdate" | "Value"  | false   | ["Session"]            | { sesh, val ->
      val.setName("New name")
      sesh.saveOrUpdate(val)
    }
    "merge"                          | "merge"        | "Value"  | false   | ["Session"]            | { sesh, val ->
      sesh.merge(new Value("merge me in"))
    }
    "persist"                        | "persist"      | "Value"  | false   | ["Session"]            | { sesh, val ->
      sesh.persist(new Value("merge me in"))
    }
    "update (Session)"               | "update"       | "Value"  | false   | ["Session"]            | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName (Session)" | "update"       | "Value"  | false   | ["Session"]            | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "delete (Session)"               | "delete"       | "Value"  | false   | ["Session"]            | { sesh, val ->
      sesh.delete(val)
    }
  }


  def "test attaches State to query created via #queryMethodName"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Query query = queryBuildMethod(session)
    List result = query.list()
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
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
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "hibernate.query.list"
          operationName "hibernate.query.list"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(3) {
          serviceName "h2"
          childOf span(2)
        }
      }
    }

    where:
    queryMethodName  | queryBuildMethod
    "createQuery"    | { sess -> sess.createQuery("from Value") }
    "getNamedQuery"  | { sess -> sess.getNamedQuery("TestNamedQuery") }
    "createSQLQuery" | { sess -> sess.createSQLQuery("SELECT * FROM Value") }
  }
}

