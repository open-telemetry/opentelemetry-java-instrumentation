import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.hibernate.MappingException
import org.hibernate.ReplicationMode
import org.hibernate.Session
import org.hibernate.SessionFactory
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

  def "test hibernate session save"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    session.save(new Value("A Hibernate value to be serialized"))
    session.save(new Value("Another value"))
    try {
      session.save(new Long(123) /* Not a valid Entity, should throw */)
    } catch (Exception e) {
      // Should set error tag.
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
          serviceName "hibernate"
          operationName "hibernate.save"
          resourceName "unknown object"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          errored true
          tags {
            errorTags(MappingException, "Unknown entity: java.lang.Long")
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(3) {
          serviceName "hibernate"
          operationName "hibernate.save"
          resourceName "Value"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(4) {
          serviceName "hibernate"
          operationName "hibernate.save"
          resourceName "Value"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "hibernate-java"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
      }
    }
  }

  def "test hibernate session replicate"() {
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
    methodName  | resource         | isError | sessionMethodTest
    "replicate" | "Value"          | false   | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate(replicated, ReplicationMode.OVERWRITE)
    }
    "replicate" | "Value"          | false   | { sesh, val ->
      Value replicated = new Value(val.getName() + " replicated")
      replicated.setId(val.getId())
      sesh.replicate("Value", replicated, ReplicationMode.OVERWRITE)
    }
    "replicate" | "unknown object" | true    | { sesh, val ->
      sesh.replicate(new Long(123) /* Not a valid entity */, ReplicationMode.OVERWRITE)
    }
    "save"      | "Value"          | false   | { sesh, val ->
      sesh.save(new Value("Another value"))
    }
  }
}
