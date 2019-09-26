// This file includes software developed at SignalFx
import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

class SpringJpaTest extends AgentTestRunner {
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
          operationName "JpaRepository.findAll"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
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
          operationName "CrudRepository.save"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // insert
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
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
          operationName "CrudRepository.save"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { //select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
        span(2) { //update
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer = repo.findByLastName("Anonymous")[0] // select

    then:
    // TODO unnecessary?
    // customer.id == savedId
    // customer.firstName == "Bill"
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "JpaCustomerRepository.findByLastName"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
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
          operationName "CrudRepository.delete"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
        span(2) { // delete
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
    }
    TEST_WRITER.clear()
  }
}
