/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.hibernate.Version
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class SpringJpaTest extends AgentInstrumentationSpecification {
  def "test object method"() {
    setup:
    def context = new AnnotationConfigApplicationContext(JpaPersistenceConfig)
    def repo = context.getBean(JpaCustomerRepository)

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    clearExportedData()

    when:
    runWithSpan("toString test") {
      repo.toString()
    }

    then:
    // Asserting that a span is NOT created for toString
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "toString test"
          attributes {
          }
        }
      }
    }
  }

  def "test CRUD"() {
    def isHibernate4 = Version.getVersionString().startsWith("4.")
    // moved inside test -- otherwise, miss the opportunity to instrument
    def context = new AnnotationConfigApplicationContext(JpaPersistenceConfig)
    def repo = context.getBean(JpaCustomerRepository)

    // when Spring JPA sets up, it issues metadata queries -- clear those traces
    clearExportedData()

    setup:
    def customer = new JpaCustomer("Bob", "Anonymous")

    expect:
    customer.id == null
    !repo.findAll().iterator().hasNext() // select

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "JpaRepository.findAll"
          kind INTERNAL
          attributes {
          }
        }
        span(1) { // select
          name "SELECT test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
      }
    }
    clearExportedData()

    when:
    repo.save(customer) // insert
    def savedId = customer.id

    then:
    customer.id != null
    assertTraces(1) {
      trace(0, 2 + (isHibernate4 ? 0 : 1)) {
        span(0) {
          name "CrudRepository.save"
          kind INTERNAL
          attributes {
          }
        }
        def offset = 0
        // hibernate5 has extra span
        if (!isHibernate4) {
          offset = 1
          span(1) {
            name "test"
            kind CLIENT
            childOf span(0)
            attributes {
              "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
              "${SemanticAttributes.DB_NAME.key}" "test"
              "${SemanticAttributes.DB_USER.key}" "sa"
              "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
              "${SemanticAttributes.DB_STATEMENT.key}" "call next value for hibernate_sequence"
            }
          }
        }
        span(1 + offset) { // insert
          name "INSERT test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^insert /
            "${SemanticAttributes.DB_OPERATION.key}" "INSERT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
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
      trace(0, 3) {
        span(0) {
          name "CrudRepository.save"
          kind INTERNAL
          attributes {
          }
        }
        span(1) { // select
          name "SELECT test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
        span(2) { // update
          name "UPDATE test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^update /
            "${SemanticAttributes.DB_OPERATION.key}" "UPDATE"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
      }
    }
    clearExportedData()

    when:
    customer = repo.findByLastName("Anonymous")[0] // select

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "JpaCustomerRepository.findByLastName"
          kind INTERNAL
          attributes {
          }
        }
        span(1) { // select
          name "SELECT test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
      }
    }
    clearExportedData()

    when:
    repo.delete(customer) // delete

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "CrudRepository.delete"
          kind INTERNAL
          attributes {
          }
        }
        span(1) { // select
          name "SELECT test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^select /
            "${SemanticAttributes.DB_OPERATION.key}" "SELECT"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
        span(2) { // delete
          name "DELETE test.JpaCustomer"
          kind CLIENT
          childOf span(0)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
            "${SemanticAttributes.DB_STATEMENT.key}" ~/^delete /
            "${SemanticAttributes.DB_OPERATION.key}" "DELETE"
            "${SemanticAttributes.DB_SQL_TABLE.key}" "JpaCustomer"
          }
        }
      }
    }
  }
}
