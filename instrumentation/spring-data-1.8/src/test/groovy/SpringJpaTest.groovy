/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spring.jpa.JpaCustomer
import spring.jpa.JpaCustomerRepository
import spring.jpa.JpaPersistenceConfig

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.INTERNAL

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
          operationName "JpaRepository.findAll"
          spanKind INTERNAL
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) { // select
          operationName ~/^select /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
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
          operationName "CrudRepository.save"
          spanKind INTERNAL
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) { // insert
          operationName ~/^insert /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
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
          operationName "CrudRepository.save"
          spanKind INTERNAL
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) { // select
          operationName ~/^select /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
        span(2) { // update
          operationName ~/^update /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
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
          operationName "JpaCustomerRepository.findByLastName"
          spanKind INTERNAL
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) { // select
          operationName ~/^select /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
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
          operationName "CrudRepository.delete"
          spanKind INTERNAL
          errored false
          tags {
            "$Tags.COMPONENT" "spring-data"
          }
        }
        span(1) { // select
          operationName ~/^select /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_STATEMENT" ~/^select /
            "span.origin.type" "org.hsqldb.jdbc.JDBCPreparedStatement"
          }
        }
        span(2) { // delete
          operationName ~/^delete /
          spanKind CLIENT
          childOf span(0)
          tags {
            "$MoreTags.SERVICE_NAME" "hsqldb"
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.DB_TYPE" "sql"
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
