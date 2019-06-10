// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import io.opentracing.tag.Tags
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.Customer
import spring.CustomerRepository
import spring.PersistenceConfig


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
    !repo.findAll().iterator().hasNext()

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "CrudRepository.findAll"
          serviceName "hsqldb"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "SimpleJpaRepository.findAll"
          serviceName "hsqldb"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(1))
        }
        span(3) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(2))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.save(customer)
    def savedId = customer.id

    then:
    customer.id != null
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "CrudRepository.save"
          serviceName "hsqldb"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "SimpleJpaRepository.save"
          serviceName "hsqldb"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(2) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(1))
        }
        span(3) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(2))
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
      trace(0, 5) {
        span(0) {
          operationName "CrudRepository.save"
          serviceName "hsqldb"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
        span(2) {
          operationName "SimpleJpaRepository.save"
          serviceName "hsqldb"
          childOf(span(0))
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(3) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(2))
        }
        span(4) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(3))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    customer = repo.findByLastName("Anonymous")[0]

    then:
    customer.id == savedId
    customer.firstName == "Bill"
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "CustomerRepository.findByLastName"
          serviceName "hsqldb"
          errored false
          tags {
            "$Tags.COMPONENT.key" "spring-data"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
        span(2) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(1))
        }
      }
      TEST_WRITER.clear()

      when:
      repo.delete(customer)

      then:
      assertTraces(1) {
        trace(0, 5) {
          span(0) {
            operationName "CrudRepository.delete"
            serviceName "hsqldb"
            errored false
            tags {
              "$Tags.COMPONENT.key" "spring-data"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(1) {
            serviceName "hsqldb"
            spanType "sql"
            childOf(span(0))
          }
          span(2) {
            operationName "SimpleJpaRepository.delete"
            serviceName "hsqldb"
            childOf(span(0))
            errored false
            tags {
              "$Tags.COMPONENT.key" "spring-data"
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              defaultTags()
            }
          }
          span(3) {
            serviceName "hsqldb"
            spanType "sql"
            childOf(span(2))
          }
          span(4) {
            serviceName "hsqldb"
            spanType "sql"
            childOf(span(3))
          }
        }
      }
      TEST_WRITER.clear()
    }
  }
}
