/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

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

public abstract class AbstractHibernateTest {
  protected static SessionFactory sessionFactory;
  protected static List<Value> prepopulated;

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  protected static void setup() {
    sessionFactory = new Configuration().configure().buildSessionFactory();
    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      prepopulated.add(new Value("Hello :) " + i));
      writer.persist(prepopulated.get(i));
    }
    writer.getTransaction().commit();
    writer.close();
  }

  @AfterAll
  protected static void cleanup() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }
}
