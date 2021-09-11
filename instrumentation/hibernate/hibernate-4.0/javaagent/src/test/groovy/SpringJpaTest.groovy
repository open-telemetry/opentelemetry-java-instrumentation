/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.hibernate.Version
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.jpa.Customer
import spring.jpa.CustomerRepository
import spring.jpa.PersistenceConfig

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class SpringJpaTest extends AgentInstrumentationSpecification {

  @Shared
  def context = new AnnotationConfigApplicationContext(PersistenceConfig)

  @Shared
  def repo = context.getBean(CustomerRepository)

  def "test CRUD"() {
    setup:
    def isHibernate4 = Version.getVersionString().startsWith("4.")
    def customer = new Customer("Bob", "Anonymous")

    expect:
    customer.id == null
    !repo.findAll().iterator().hasNext()

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "Session"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "SELECT Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "SELECT test.Customer"
          kind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/select ([^.]+)\.id([^,]*), ([^.]+)\.firstName([^,]*), ([^.]+)\.lastName(.*)from Customer(.*)/
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
      }
    }
    clearExportedData()

    when:
    repo.save(customer)
    def savedId = customer.id

    then:
    customer.id != null
    assertTraces(1) {
      trace(0, 4 + (isHibernate4 ? 0 : 1)) {
        span(0) {
          name "Session"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "Session.persist spring.jpa.Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        if (!isHibernate4) {
          span(2) {
            name "test"
            kind CLIENT
            childOf span(1)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_STATEMENT.key}" "call next value for hibernate_sequence"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            }
          }
          span(3) {
            name "Transaction.commit"
            kind INTERNAL
            childOf span(0)
            attributes {
            }
          }
          span(4) {
            name "INSERT test.Customer"
            kind CLIENT
            childOf span(3)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" ~/insert into Customer \(.*\) values \(.*, \?, \?\)/
              "${SemanticAttributes.DB_OPERATION.key}" "INSERT"
              "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
            }
          }
        } else {
          span(2) {
            name "INSERT test.Customer"
            kind CLIENT
            childOf span(1)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" ~/insert into Customer \(.*\) values \(.*, \?, \?\)/
              "${SemanticAttributes.DB_OPERATION.key}" "INSERT"
              "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
            }
          }
          span(3) {
            name "Transaction.commit"
            kind INTERNAL
            childOf span(0)
            attributes {
            }
          }
        }
      }
    }
    clearExportedData()

    when:
    customer.firstName = "Bill"
    repo.save(customer)

    then:
    customer.id == savedId
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "Session"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "Session.merge spring.jpa.Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "SELECT test.Customer"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/select ([^.]+)\.id([^,]*), ([^.]+)\.firstName([^,]*), ([^.]+)\.lastName (.*)from Customer (.*)where ([^.]+)\.id( ?)=( ?)\?/
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
          }
        }
        span(3) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(4) {
          name "UPDATE test.Customer"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" "update Customer set firstName=?, lastName=? where id=?"
            "${SemanticAttributes.DB_OPERATION.key}" "UPDATE"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
          }
        }
      }
    }
    clearExportedData()

    when:
    customer = repo.findByLastName("Anonymous")[0]

    then:
    customer.id == savedId
    customer.firstName == "Bill"
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "Session"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "SELECT Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(2) {
          name "SELECT test.Customer"
          kind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/select ([^.]+)\.id([^,]*), ([^.]+)\.firstName([^,]*), ([^.]+)\.lastName (.*)from Customer (.*)(where ([^.]+)\.lastName( ?)=( ?)\?|)/
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
          }
        }
      }
    }
    clearExportedData()

    when:
    repo.delete(customer)

    then:
    assertTraces(1) {
      trace(0, 6 + (isHibernate4 ? 0 : 1)) {
        span(0) {
          name "Session"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        def offset = 0
        if (!isHibernate4) {
          offset = 2
          span(1) {
            name ~/Session.(get|find) spring.jpa.Customer/
            kind INTERNAL
            childOf span(0)
            attributes {
            }
          }
          span(2) {
            name "SELECT test.Customer"
            kind CLIENT
            childOf span(1)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" ~/select ([^.]+)\.id([^,]*), ([^.]+)\.firstName([^,]*), ([^.]+)\.lastName (.*)from Customer (.*)where ([^.]+)\.id( ?)=( ?)\?/
              "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
              "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
            }
          }
        }
        span(1 + offset) {
          name "Session.merge spring.jpa.Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        if (isHibernate4) {
          offset = 1
          span(2) {
            name "SELECT test.Customer"
            kind CLIENT
            childOf span(1)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" ~/select ([^.]+)\.id([^,]*), ([^.]+)\.firstName([^,]*), ([^.]+)\.lastName (.*)from Customer (.*)where ([^.]+)\.id( ?)=( ?)\?/
              "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
              "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
            }
          }
        }
        span(2 + offset) {
          name "Session.delete spring.jpa.Customer"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(3 + offset) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
          }
        }
        span(4 + offset) {
          name "DELETE test.Customer"
          kind CLIENT
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" "delete from Customer where id=?"
            "${SemanticAttributes.DB_OPERATION.key}" "DELETE"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "Customer"
          }
        }
      }
    }
  }
}
