// This file includes software developed at SignalFx

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

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
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "JpaRepository.findAll"
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) { // select
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
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
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.save"
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) { // insert
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^insert /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^insert /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
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
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.save"
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) { // select
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
        span(2) { // update
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^update /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^update /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
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
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "JpaCustomerRepository.findByLastName"
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) { // select
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete(customer) // delete

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "repository.operation"
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "CrudRepository.delete"
            "$Tags.COMPONENT" "spring-data"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
          }
        }
        span(1) { // select
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^select /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
        span(2) { // delete
          childOf(span(0))
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$MoreTags.RESOURCE_NAME" ~/^delete /
            "$MoreTags.SPAN_TYPE" "sql"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^delete /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
      }
    }
    TEST_WRITER.clear()
  }
}
