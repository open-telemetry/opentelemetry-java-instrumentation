import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.Customer
import spring.CustomerRepository
import spring.PersistenceConfig

// Modified from signalfx version because of differences in JDBC integration
class SpringJpaTest extends AgentTestRunner {

  @Shared
  def context = new AnnotationConfigApplicationContext(PersistenceConfig)

  @Shared
  def repo = context.getBean(CustomerRepository)

  def "test CRUD"() {
    setup:
    def customer = new Customer("Bob", "Anonymous")

    expect:
    customer.id == null
    !repo.findAll().iterator().hasNext() // select

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "CrudRepository.findAll"
          serviceName "spring-data"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "SimpleJpaRepository.findAll"
          serviceName "spring-data"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(1))
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
        span(1) {
          operationName "SimpleJpaRepository.save"
          serviceName "spring-data"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) { // insert
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(1))
        }

        // commit?
      }
    }
    TEST_WRITER.clear()

    when:
    customer.firstName = "Bill"
    repo.save(customer)

    then:
    customer.id == savedId
    assertTraces(1) {
      trace(0, 4) {
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
        span(1) { // update
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
        span(2) {
          operationName "SimpleJpaRepository.save"
          serviceName "spring-data"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(3) { // select
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(2))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer = repo.findByLastName("Anonymous")[0] // select

    then:
    customer.id == savedId
    customer.firstName == "Bill"
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "CustomerRepository.findByLastName"
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
      TEST_WRITER.clear()

      when:
      repo.delete(customer) //delete

      then:
      assertTraces(1) {
        trace(0, 4) {
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
          span(1) { // delete
            serviceName "hsqldb"
            spanType "sql"
            childOf(span(0))
          }
          span(2) {
            operationName "SimpleJpaRepository.delete"
            serviceName "spring-data"
            childOf(span(0))
            errored false
            tags {
              "$Tags.COMPONENT.key" "spring-data"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(3) { // select
            serviceName "hsqldb"
            spanType "sql"
            childOf(span(2))
          }
        }
      }
      TEST_WRITER.clear()
    }
  }
}
