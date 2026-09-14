/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.procedure.call.v4_3;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.procedure.ProcedureCall;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DefaultEnablementTest {

  private static final String DATABASE_URL = "jdbc:hsqldb:mem:defaultEnablement";
  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static SessionFactory sessionFactory;

  @BeforeAll
  @SuppressWarnings("deprecation") // buildSessionFactory
  static void setUp() throws SQLException {
    sessionFactory =
        new Configuration()
            .configure()
            .setProperty("hibernate.connection.url", DATABASE_URL)
            .buildSessionFactory();

    try (Connection connection = DriverManager.getConnection(DATABASE_URL, "sa", "1");
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE PROCEDURE DEFAULT_ENABLEMENT_PROC() MODIFIES SQL DATA "
              + "BEGIN ATOMIC INSERT INTO Value VALUES (421, 'fred'); END");
    }
  }

  @AfterAll
  static void cleanUp() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @Test
  void defaultEnablement() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          try {
            session.beginTransaction();
            ProcedureCall call = session.createStoredProcedureCall("DEFAULT_ENABLEMENT_PROC");
            call.getOutputs();
            session.getTransaction().commit();
          } finally {
            session.close();
          }
        });

    if (V3_PREVIEW) {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span ->
                      span.hasName("ProcedureCall.getOutputs DEFAULT_ENABLEMENT_PROC")
                          .hasParent(trace.getSpan(0)),
                  span -> span.hasKind(CLIENT).hasParent(trace.getSpan(1)),
                  span -> span.hasName("Transaction.commit").hasParent(trace.getSpan(0))));
    }
  }
}
