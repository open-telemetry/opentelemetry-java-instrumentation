import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import org.hibernate.Query
import org.hibernate.Session

import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

class QueryTest extends AbstractHibernateTest {

  def "test hibernate query.#queryMethodName single call"() {
    setup:

    // With Transaction
    Session session = sessionFactory.openSession()
    session.beginTransaction()
    queryInteraction(session)
    session.getTransaction().commit()
    session.close()

    // Without Transaction
    if (!requiresTransaction) {
      session = sessionFactory.openSession()
      queryInteraction(session)
      session.close()
    }

    expect:
    assertTraces(requiresTransaction ? 1 : 2) {
      // With Transaction
      trace(0, 4) {
        span(0) {
          operationName "hibernate.session"
          spanKind INTERNAL
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(1) {
          operationName "hibernate.$queryMethodName"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" resource
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.DB_STATEMENT" queryMethodName == "iterate" ? null : String
          }
        }
        span(2) {
          spanKind CLIENT
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" String
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" String
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "hibernate.transaction.commit"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
      }
      if (!requiresTransaction) {
        // Without Transaction
        trace(1, 3) {
          span(0) {
            operationName "hibernate.session"
            spanKind INTERNAL
            parent()
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
              "$Tags.COMPONENT" "java-hibernate"
            }
          }
          span(1) {
            operationName "hibernate.$queryMethodName"
            spanKind INTERNAL
            childOf span(0)
            tags {
              "$MoreTags.SERVICE_NAME" "hibernate"
              "$MoreTags.RESOURCE_NAME" resource
              "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
              "$Tags.COMPONENT" "java-hibernate"
              "$Tags.DB_STATEMENT" queryMethodName == "iterate" ? null : String
            }
          }
          span(2) {
            spanKind CLIENT
            childOf span(1)
            tags {
              "$MoreTags.SERVICE_NAME" "h2"
              "$MoreTags.RESOURCE_NAME" ~/^select /
              "$MoreTags.SPAN_TYPE" "sql"
              "$Tags.COMPONENT" "java-jdbc-prepared_statement"
              "$Tags.DB_TYPE" "h2"
              "$Tags.DB_INSTANCE" "db1"
              "$Tags.DB_USER" "sa"
              "$Tags.DB_STATEMENT" ~/^select /
              "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
            }
          }
        }
      }
    }

    where:
    queryMethodName       | resource     | requiresTransaction | queryInteraction
    "query.list"          | "Value"      | false               | { sess ->
      Query q = sess.createQuery("from Value")
      q.list()
    }
    "query.executeUpdate" | null         | true                | { sess ->
      Query q = sess.createQuery("update Value set name = 'alyx'")
      q.executeUpdate()
    }
    "query.uniqueResult"  | "Value"      | false               | { sess ->
      Query q = sess.createQuery("from Value where id = 1")
      q.uniqueResult()
    }
    "iterate"             | "from Value" | false               | { sess ->
      Query q = sess.createQuery("from Value")
      q.iterate()
    }
    "query.scroll"        | null         | false               | { sess ->
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
      trace(0, 4) {
        span(0) {
          operationName "hibernate.session"
          spanKind INTERNAL
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(1) {
          operationName "hibernate.iterate"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.RESOURCE_NAME" "from Value"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
        span(2) {
          spanKind CLIENT
          childOf span(1)
          tags {
            "$MoreTags.SERVICE_NAME" "h2"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "db1"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.h2.jdbc.JdbcPreparedStatement"
          }
        }
        span(3) {
          operationName "hibernate.transaction.commit"
          spanKind INTERNAL
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hibernate"
            "$MoreTags.SPAN_TYPE" SpanTypes.HIBERNATE
            "$Tags.COMPONENT" "java-hibernate"
          }
        }
      }
    }
  }

}
