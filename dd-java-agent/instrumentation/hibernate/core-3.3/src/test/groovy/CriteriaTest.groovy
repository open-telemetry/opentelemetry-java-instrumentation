import datadog.trace.api.SpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.api.Tags
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
          operationName "hibernate.session"
          parent()
          tags {
            "$DDTags.SERVICE_NAME" "hibernate"
            "$DDTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) {
          operationName "hibernate.criteria.$methodName"
          childOf span(0)
          tags {
            "$DDTags.SERVICE_NAME" "hibernate"
            "$DDTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(2) {
          childOf span(1)
          tags {
            "$DDTags.SERVICE_NAME" "h2"
            "$DDTags.RESOURCE_NAME" ~/^select /
            "$DDTags.SPAN_TYPE" "sql"
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
            "$DDTags.SERVICE_NAME" "hibernate"
            "$DDTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
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
