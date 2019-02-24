import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.hibernate.Query
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import spock.lang.Shared

class QueryTest extends AgentTestRunner {

  @Shared
  private SessionFactory sessionFactory

  def setupSpec() {
    final StandardServiceRegistry registry =
      new StandardServiceRegistryBuilder()
        .configure()
        .build()
    try {
      sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory()
    } catch (Exception e) {
      StandardServiceRegistryBuilder.destroy(registry)
      return
    }

    Session session = sessionFactory.openSession()
    session.beginTransaction()
    session.save(new Value("A Hibernate value to be serialized"))
    session.save(new Value("Another value"))
    session.getTransaction().commit()
    session.close()
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }

  def "test hibernate query.#queryMethodName"() {
    setup:

    Session session = sessionFactory.openSession()
    session.beginTransaction()
    queryInteraction(session)
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
          resourceName "hibernate.query.$queryMethodName"
          operationName "hibernate.query.$queryMethodName"
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
    queryMethodName | isError | queryInteraction
    "list"          | false   | { sess ->
      Query q = sess.createQuery("from Value")
      q.list()
    }
    "executeUpdate" | false   | { sess ->
      Query q = sess.createQuery("update Value set name = 'alyx'")
      q.executeUpdate()
    }
    "uniqueResult"  | false   | { sess ->
      Query q = sess.createQuery("from Value where id = 1")
      q.uniqueResult()
    }
    "iterate"       | false   | { sess ->
      Query q = sess.createQuery("from Value")
      q.iterate()
    }
    "scroll"        | false   | { sess ->
      Query q = sess.createQuery("from Value")
      q.scroll()
    }
  }

}
