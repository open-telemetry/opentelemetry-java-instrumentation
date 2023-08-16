/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractHibernateTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected static SessionFactory sessionFactory;
  protected static List<Value> prepopulated;

  @BeforeAll
  @SuppressWarnings("deprecation") // buildSessionFactory
  static void setUp() {
    sessionFactory = new Configuration().configure().buildSessionFactory();

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      prepopulated.add(new Value("Hello :) " + i));
      writer.save(prepopulated.get(i));
    }
    writer.getTransaction().commit();
    writer.close();
  }

  @AfterAll
  static void cleanUp() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }
}
