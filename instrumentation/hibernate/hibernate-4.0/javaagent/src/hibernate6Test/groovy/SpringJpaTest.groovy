/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared
import spring.jpa.Customer
import spring.jpa.CustomerRepository
import spring.jpa.PersistenceConfig

import static io.opentelemetry.api.trace.SpanKind.CLIENT

/**
 * Unfortunately this test verifies that our hibernate instrumentation doesn't currently work with Spring Data Repositories.
 */
class SpringJpaTest extends AgentInstrumentationSpecification {

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
          name "SELECT test.Customer"
          kind CLIENT
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
      }
    }
    clearExportedData()

    when:
    repo.save(customer)
    def savedId = customer.id

    then:
    customer.id != null
    // Behavior changed in new version:
    def extraTrace = traces.size() == 2
    assertTraces(extraTrace ? 2 : 1) {
      if (extraTrace) {
        trace(0, 1) {
          span(0) {
            name "test"
            kind CLIENT
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_STATEMENT.key}" "call next value for hibernate_sequence"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            }
          }
        }
      }
      trace(extraTrace ? 1 : 0, 1) {
        span(0) {
          name "INSERT test.Customer"
          kind CLIENT
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
      }
    }
    clearExportedData()

    when:
    customer.firstName = "Bill"
    repo.save(customer)

    then:
    customer.id == savedId
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
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
      }
      trace(1, 1) {
        span(0) {
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
      trace(0, 1) {
        span(0) {
          name "SELECT test.Customer"
          kind CLIENT
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
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
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
      }
      trace(1, 1) {
        span(0) {
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
