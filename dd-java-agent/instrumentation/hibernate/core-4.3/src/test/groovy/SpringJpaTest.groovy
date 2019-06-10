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
      trace(0, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_"
          spanType "sql"
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
      if (extraTrace) {
        trace(0, 1) {
          span(0) {
            serviceName "hsqldb"
            resourceName "call next value for hibernate_sequence"
            spanType "sql"
          }
        }
      }
      trace(extraTrace ? 1 : 0, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName ~/insert into Customer \(.*\) values \(.*, \?, \?\)/
          spanType "sql"
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
      trace(0, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
          spanType "sql"
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "update Customer set firstName=?, lastName=? where id=?"
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
      trace(0, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "select customer0_.id as id1_0_, customer0_.firstName as firstNam2_0_, customer0_.lastName as lastName3_0_ from Customer customer0_ where customer0_.lastName=?"
          spanType "sql"
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.delete(customer)

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "select customer0_.id as id1_0_0_, customer0_.firstName as firstNam2_0_0_, customer0_.lastName as lastName3_0_0_ from Customer customer0_ where customer0_.id=?"
          spanType "sql"
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "hsqldb"
          resourceName "delete from Customer where id=?"
          spanType "sql"
        }
      }
    }
    TEST_WRITER.clear()
  }
}
