import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import lombok.NonNull
import org.hibernate.*
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import spock.lang.Shared

class SessionTest extends AgentTestRunner {

  @Shared
  private SessionFactory sessionFactory

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
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }

  @lombok.Value
  static class SessionTypeTest {
    // Lombok can't get the types right for the constructor.
    SessionTypeTest(Closure fn) {
      sessionBuilder = fn
    }

    @NonNull
    Closure sessionBuilder
    @NonNull
    Value value = new Value("Hello :)")
  }

  def "test hibernate #testName"() {
    setup:
    // Test two different types of Session. Groovy doesn't allow testing the cross-product/combinations of two data
    // tables, so we get this hack instead.
    Map<String, SessionTypeTest> sessionTests = new HashMap<>();
    sessionTests.put("Session", new SessionTypeTest({ return sessionFactory.openSession() }))
    sessionTests.put("StatelessSession", new SessionTypeTest({ return sessionFactory.openStatelessSession() }))

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession()
    writer.beginTransaction()
    for (SessionTypeTest sessionTypeTest : sessionTests.values()) {
      writer.save(sessionTypeTest.value)
    }
    writer.getTransaction().commit()
    writer.close()
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    // Test for each implementation of Session.
    for (String sessionImplementation : sessionImplementations) {
      SessionTypeTest sessionTypeTest = sessionTests.get(sessionImplementation)
      def session = sessionTypeTest.getSessionBuilder().call()
      session.beginTransaction()

      try {
        sessionMethodTest.call(session, sessionTypeTest.getValue())
      } catch (Exception e) {
        // We expected this, we should see the error field set on the span.
      }

      session.getTransaction().commit()
      session.close()
    }

    expect:
    assertTraces(sessionImplementations.size()) {
      for (int i = 0; i < sessionImplementations.size(); i++) {
        trace(i, 3) {
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
        }
      }
    }

    where:
    testName                  | methodName     | resource         | isError | sessionImplementations          | sessionMethodTest
    "replicate"               | "replicate"    | "Value"          | false   | ["Session"]                     | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate by entityName" | "replicate"    | "Value"          | false   | ["Session"]                     | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate("Value", replicated, ReplicationMode.OVERWRITE)
    }
    "failed replicate"        | "replicate"    | "unknown object" | true    | ["Session"]                     | { sesh, val ->
      sesh.replicate(new Long(123) /* Not a valid entity */, ReplicationMode.OVERWRITE)
    }
    "save"                    | "save"         | "Value"          | false   | ["Session"]                     | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
    "saveOrUpdate save"       | "saveOrUpdate" | "Value"          | false   | ["Session"]                     | { sesh, val ->
      sesh.saveOrUpdate(new Value("Value"))
    }
    "saveOrUpdate update"     | "saveOrUpdate" | "Value"          | false   | ["Session"]                     | { sesh, val ->
      val.setName("New name")
      sesh.saveOrUpdate(val)
    }
    "update"                  | "update"       | "Value"          | false   | ["Session", "StatelessSession"] | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName"    | "update"       | "Value"          | false   | ["Session", "StatelessSession"] | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "merge"                   | "merge"        | "Value"          | false   | ["Session"]                     | { sesh, val ->
      sesh.merge(new Value("merge me in"))
    }
    "persist"                 | "persist"      | "Value"          | false   | ["Session"]                     | { sesh, val ->
      sesh.persist(new Value("merge me in"))
    }
    "lock"                    | "lock"         | "Value"          | false   | ["Session"]                     | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh"                 | "refresh"      | "Value"          | false   | ["Session", "StatelessSession"] | { sesh, val ->
      sesh.refresh(val)
    }
    "delete"                  | "delete"       | "Value"          | false   | ["Session", "StatelessSession"] | { sesh, val ->
      sesh.delete(val)
    }
    "get"                     | "get"          | "Value"          | false   | ["Session", "StatelessSession"] | { sesh, val ->
      sesh.get("Value", val.getId())
    }
  }
}

