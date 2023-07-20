/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.ArrayList;
import java.util.List;

abstract class AbstractHibernateTest extends AgentInstrumentationSpecification {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();
  protected static SessionFactory sessionFactory;
  protected static List<Value> prepopulated;

  @BeforeAll
  static void setUp() {
    sessionFactory = new AnnotationConfiguration().configure().buildSessionFactory();

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
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
