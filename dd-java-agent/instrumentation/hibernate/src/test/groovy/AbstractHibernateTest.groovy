import datadog.trace.agent.test.AgentTestRunner
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import spock.lang.Shared

abstract class AbstractHibernateTest extends AgentTestRunner {

  @Shared
  protected SessionFactory sessionFactory

  @Shared
  protected List<Value> prepopulated

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

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession()
    writer.beginTransaction()
    prepopulated = new ArrayList<>()
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i))
      writer.save(prepopulated.get(i))
    }
    writer.getTransaction().commit()
    writer.close()
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }
}
