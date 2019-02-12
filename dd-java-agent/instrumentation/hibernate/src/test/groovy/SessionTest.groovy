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

  def "test hibernate #testName"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Value value = new Value("Hello :)")
    session.save(value)
    session.getTransaction().commit()
    session.close()

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    session = sessionFactory.openSession()
    session.beginTransaction()

    try {
      sessionMethodTest.call(session, value)
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

    where:
    testName                  | methodName     | resource         | isError | sessionMethodTest
    "replicate"               | "replicate"    | "Value"          | false   | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate by entityName" | "replicate"    | "Value"          | false   | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate("Value", replicated, ReplicationMode.OVERWRITE)
    }
    "failed replicate"        | "replicate"    | "unknown object" | true    | { sesh, val ->
      sesh.replicate(new Long(123) /* Not a valid entity */, ReplicationMode.OVERWRITE)
    }
    "save"                    | "save"         | "Value"          | false   | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
    "saveOrUpdate save"       | "saveOrUpdate" | "Value"          | false   | { sesh, val ->
      sesh.saveOrUpdate(new Value("Value"))
    }
    "saveOrUpdate update"     | "saveOrUpdate" | "Value"          | false   | { sesh, val ->
      val.setName("New name")
      sesh.saveOrUpdate(val)
    }
    "update"                  | "update"       | "Value"          | false   | { sesh, val ->
      val.setName("New name")
      sesh.update(val)
    }
    "update by entityName"    | "update"       | "Value"          | false   | { sesh, val ->
      val.setName("New name")
      sesh.update("Value", val)
    }
    "merge"                   | "merge"        | "Value"          | false   | { sesh, val ->
      sesh.merge(new Value("merge me in"))
    }
    "persist"                 | "persist"      | "Value"          | false   | { sesh, val ->
      sesh.persist(new Value("merge me in"))
    }
    "lock"                    | "lock"         | "Value"          | false   | { sesh, val ->
      sesh.lock(val, LockMode.READ)
    }
    "refresh"                 | "refresh"      | "Value"          | false   | { sesh, val ->
      sesh.refresh(val)
    }
    "delete"                  | "delete"       | "Value"          | false   | { sesh, val ->
      sesh.delete(val)
    }
    "get"                     | "get"          | "Value"          | false   | { sesh, val ->
      sesh.get("Value", val.getId())
    }
  }
}

