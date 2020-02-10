// This file includes software developed at SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.bootstrap.instrumentation.api.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class SpringJpaTest extends AgentTestRunner {
  def "test object method"() {
    setup:
    def context = new AnnotationConfigApplicationContext(JpaPersistenceConfig)
    def repo = context.getBean(JpaCustomerRepository)

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    TEST_WRITER.clear()

    when:
    runUnderTrace("toString test") {
      repo.toString()
    }

    then:
    // Asserting that a span is NOT created for toString
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "toString test"
          tags {
            defaultTags()
          }
        }
      }
    }
  }

  def "test CRUD"() {
    // moved inside test -- otherwise, miss the opportunity to instrument
    def context = new AnnotationConfigApplicationContext(JpaPersistenceConfig)
    def repo = context.getBean(JpaCustomerRepository)

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    TEST_WRITER.clear()

    setup:
    def customer = new JpaCustomer("Bob", "Anonymous")

    expect:
    customer.id == null
    !repo.findAll().iterator().hasNext() // select

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "repository.operation"
          resourceName "JpaRepository.findAll"
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.save(customer) // insert
    def savedId = customer.id

    then:
    customer.id != null
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "repository.operation"
          resourceName "CrudRepository.save"
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // insert
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer.firstName = "Bill"
    repo.save(customer)

    then:
    customer.id == savedId
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "repository.operation"
          resourceName "CrudRepository.save"
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { //select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
        span(2) { //update
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer = repo.findByLastName("Anonymous")[0] // select

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "repository.operation"
          resourceName "JpaCustomerRepository.findByLastName"
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete(customer) //delete

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "repository.operation"
          resourceName "CrudRepository.delete"
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
        span(2) { // delete
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()
  }
}
