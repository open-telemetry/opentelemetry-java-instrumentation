/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.exception.SQLGrammarException
import org.hibernate.procedure.ProcedureCall
import org.junit.jupiter.api.Assumptions
import spock.lang.Shared

import javax.persistence.ParameterMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.StatusCode.ERROR

class ProcedureCallTest extends AgentInstrumentationSpecification {

  @Shared
  protected SessionFactory sessionFactory

  @Shared
  protected List<Value> prepopulated

  def setupSpec() {
    sessionFactory = new Configuration().configure().buildSessionFactory()
    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession()
    writer.beginTransaction()
    prepopulated = new ArrayList<>()
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i))
      writer.save(prepopulated.get(i))
    }
    writer.getTransaction().commit()
    writer.close()

    // Create a stored procedure.
    Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "1")
    Statement stmt = conn.createStatement()
    stmt.execute("CREATE PROCEDURE TEST_PROC() MODIFIES SQL DATA BEGIN ATOMIC INSERT INTO Value VALUES (420, 'fred'); END")
    stmt.close()
    conn.close()
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }

  def callProcedure(ProcedureCall call) {
    try {
      call.getOutputs()
    } catch (Exception exception) {
      // ignore failures on hibernate 6 where this functionality has not been implemented yet
      Assumptions.assumeFalse("org.hibernate.NotYetImplementedFor6Exception" == exception.getClass().getName())
      throw exception
    }
  }

  def "test ProcedureCall"() {
    setup:

    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()

      ProcedureCall call = session.createStoredProcedureCall("TEST_PROC")
      callProcedure(call)

      session.getTransaction().commit()
      session.close()
    }

    expect:
    def sessionId
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "ProcedureCall.getOutputs TEST_PROC"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }
        span(2) {
          name "test"
          kind CLIENT
          childOf span(1)
          attributes {
            "${SemanticAttributes.DB_SYSTEM.key}" "hsqldb"
            "${SemanticAttributes.DB_NAME.key}" "test"
            "${SemanticAttributes.DB_USER.key}" "sa"
            "${SemanticAttributes.DB_STATEMENT.key}" "{call TEST_PROC()}"
            "${SemanticAttributes.DB_CONNECTION_STRING.key}" "hsqldb:mem:"
          }
        }
        span(3) {
          kind INTERNAL
          name "Transaction.commit"
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
      }
    }
  }

  def "test failing ProcedureCall"() {
    setup:

    runWithSpan("parent") {
      Session session = sessionFactory.openSession()
      session.beginTransaction()

      ProcedureCall call = session.createStoredProcedureCall("TEST_PROC")
      def parameterRegistration = call.registerParameter("nonexistent", Long, ParameterMode.IN)
      Assumptions.assumeTrue(parameterRegistration.metaClass.getMetaMethod("bindValue", Object) != null)
      parameterRegistration.bindValue(420L)
      try {
        callProcedure(call)
      } catch (Exception e) {
        // We expected this.
      }

      session.getTransaction().commit()
      session.close()
    }

    expect:
    def sessionId
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "ProcedureCall.getOutputs TEST_PROC"
          kind INTERNAL
          childOf span(0)
          status ERROR
          errorEvent(SQLGrammarException, "could not prepare statement")
          attributes {
            "hibernate.session_id" {
              sessionId = it
              it instanceof String
            }
          }
        }
        span(2) {
          name "Transaction.commit"
          kind INTERNAL
          childOf span(0)
          attributes {
            "hibernate.session_id" sessionId
          }
        }
      }
    }
  }
}

