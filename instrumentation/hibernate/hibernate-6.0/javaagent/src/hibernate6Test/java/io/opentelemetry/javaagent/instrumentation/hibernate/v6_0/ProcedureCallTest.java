/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import jakarta.persistence.ParameterMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.query.procedure.ProcedureParameter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ProcedureCallTest {
  protected static SessionFactory sessionFactory;
  protected static List<Value> prepopulated;

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setup() throws SQLException {
    sessionFactory =
        new Configuration().configure("procedure-call-hibernate.cfg.xml").buildSessionFactory();
    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i));
      writer.persist(prepopulated.get(i));
    }
    writer.getTransaction().commit();
    writer.close();

    // Create a stored procedure.
    Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "1");
    Statement stmt = conn.createStatement();
    stmt.execute(
        "CREATE PROCEDURE TEST_PROC() MODIFIES SQL DATA BEGIN ATOMIC INSERT INTO Value VALUES (420, 'fred'); END");
    stmt.close();
    conn.close();
  }

  @AfterAll
  static void cleanup() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testProcedureCall() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();

          ProcedureCall call = session.createStoredProcedureCall("TEST_PROC");
          call.getOutputs();

          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("ProcedureCall.getOutputs TEST_PROC")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("CALL test.TEST_PROC")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            equalTo(maybeStable(DB_STATEMENT), "{call TEST_PROC()}"),
                            equalTo(maybeStable(DB_OPERATION), "CALL")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(AttributeKey.stringKey("hibernate.session_id"))))));
  }

  @Test
  void testFailingProcedureCall() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();

          ProcedureCall call = session.createStoredProcedureCall("TEST_PROC");
          ProcedureParameter<Long> parameterRegistration =
              call.registerParameter("nonexistent", Long.class, ParameterMode.IN);
          call.setParameter(parameterRegistration, 420L);
          try {
            call.getOutputs();
          } catch (RuntimeException e) {
            // We expected this.
          }
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("ProcedureCall.getOutputs TEST_PROC")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            AttributeKey.stringKey("exception.type"),
                                            "org.hibernate.exception.SQLGrammarException"),
                                        satisfies(
                                            AttributeKey.stringKey("exception.message"),
                                            val -> val.startsWith("could not prepare statement")),
                                        satisfies(
                                            AttributeKey.stringKey("exception.stacktrace"),
                                            val -> val.isNotNull())))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(AttributeKey.stringKey("hibernate.session_id"))))));
  }
}
