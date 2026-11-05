/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;

class DefaultEnablementTest extends AbstractHibernateTest {

  private static final boolean INSTRUMENTATION_ENABLED =
      Boolean.getBoolean("otel.instrumentation.hibernate.enabled");

  @Test
  void defaultEnablement() {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Session session = sessionFactory.openSession()) {
            session.find(Value.class, prepopulated.get(0).getId());
          }
        });

    if (INSTRUMENTATION_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasName("Session.find " + Value.class.getName()),
                  span -> span.hasName("select Value")));
    } else {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    }
  }
}
