import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.hibernate.Query
import org.hibernate.Session

class QueryTest extends AbstractHibernateTest {

  def "test hibernate query.#queryMethodName single call"() {
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
            "$Tags.COMPONENT.key" "java-hibernate"
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
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "$resource"
          operationName "hibernate.$queryMethodName"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
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
    queryMethodName       | isError | resource                         | queryInteraction
    "query.list"          | false   | "Value"                          | { sess ->
      Query q = sess.createQuery("from Value")
      q.list()
    }
    "query.executeUpdate" | false   | "update Value set name = 'alyx'" | { sess ->
      Query q = sess.createQuery("update Value set name = 'alyx'")
      q.executeUpdate()
    }
    "query.uniqueResult"  | false   | "Value"                          | { sess ->
      Query q = sess.createQuery("from Value where id = 1")
      q.uniqueResult()
    }
    "iterate"             | false   | "from Value"                     | { sess ->
      Query q = sess.createQuery("from Value")
      q.iterate()
    }
    "query.scroll"        | false   | "from Value"                     | { sess ->
      Query q = sess.createQuery("from Value")
      q.scroll()
    }
  }

  def "test hibernate query.iterate"() {
    setup:

    Session session = sessionFactory.openSession()
    session.beginTransaction()
    Query q = session.createQuery("from Value")
    Iterator it = q.iterate()
    while (it.hasNext()) {
      it.next()
    }
    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(0, 6) {
        span(0) {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
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
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(2) {
          serviceName "hibernate"
          resourceName "hibernate.iterator.next"
          operationName "hibernate.iterator.next"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(3) {
          serviceName "hibernate"
          resourceName "hibernate.iterator.next"
          operationName "hibernate.iterator.next"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(4) {
          serviceName "hibernate"
          resourceName "from Value"
          operationName "hibernate.iterate"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT.key" "java-hibernate"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HIBERNATE
            defaultTags()
          }
        }
        span(5) {
          serviceName "h2"
          childOf span(4)
        }
      }
    }
  }

}
