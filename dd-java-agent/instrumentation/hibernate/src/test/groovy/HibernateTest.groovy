import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import spock.lang.Shared

class HibernateTest extends AgentTestRunner {

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
      System.out.println(e.toString());
      StandardServiceRegistryBuilder.destroy(registry);
    }

  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }

  def "test hibernate session"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    session.save(new Value("A Hibernate value to be serialized"))
    session.save(new Value("Another value"))
    session.getTransaction().commit()
    session.close()

//    session = sessionFactory.openSession()
//    session.beginTransaction()
//    List result = session.createQuery("from Value").list()
//    for (Value value : (List<Value>) result) {
//      System.out.println(value.getName())
//    }
//    session.getTransaction().commit()
//    session.close()

    expect:
//    result.size() == 2

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
      }
    }
  }

}
