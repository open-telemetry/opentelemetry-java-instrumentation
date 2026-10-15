/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

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
          Session session = sessionFactory.openSession();
          try {
            session.get(Value.class, prepopulated.get(0).getId());
          } finally {
            session.close();
          }
        });

    if (INSTRUMENTATION_ENABLED) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent"),
                  span -> span.hasName("Session.get " + Value.class.getName()),
                  span -> span.hasName("select Value")));
    } else {
      testing.waitAndAssertTraces(
          trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent")));
    }
  }
}
