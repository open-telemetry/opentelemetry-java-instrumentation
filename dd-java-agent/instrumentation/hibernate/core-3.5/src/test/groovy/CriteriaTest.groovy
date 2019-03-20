import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import org.hibernate.Criteria
import org.hibernate.Session
import org.hibernate.criterion.Order
import org.hibernate.criterion.Restrictions

class CriteriaTest extends AbstractHibernateTest {

  def "test criteria.#methodName"() {
    setup:
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Criteria criteria = session.createCriteria(Value)
      .add(Restrictions.like("name", "Hello"))
      .addOrder(Order.desc("name"))
    interaction.call(criteria)
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
          resourceName "hibernate.criteria.$methodName"
          operationName "hibernate.criteria.$methodName"
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
    methodName     | interaction
    "list"         | { c -> c.list() }
    "uniqueResult" | { c -> c.uniqueResult() }
    "scroll"       | { c -> c.scroll() }
  }
}
