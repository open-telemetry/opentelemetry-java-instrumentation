import datadog.trace.agent.test.AgentTestRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.Customer
import spring.CustomerRepository
import spring.PersistenceConfig


/**
 * Unfortunately this test verifies that our hibernate instrumentation doesn't currently work with Spring Data Repositories.
 */
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
      trace(0, 2) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.save(customer)
    def savedId = customer.id

    then:
    customer.id != null
    // Behavior changed in new version:
    def extraTrace = TEST_WRITER.size() == 2
    assertTraces(extraTrace ? 2 : 1) {
      trace(0, 2) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
      if (extraTrace) {
        trace(1, 1) {
          span(0) {
            serviceName "hsqldb"
            spanType "sql"
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
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
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
      trace(0, 2) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete(customer)

    then:
    assertTraces(2) {
      trace(0, 2) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
        span(1) {
          serviceName "hsqldb"
          spanType "sql"
          childOf(span(0))
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "hsqldb"
          spanType "sql"
        }
      }
    }
    TEST_WRITER.clear()
  }
}
