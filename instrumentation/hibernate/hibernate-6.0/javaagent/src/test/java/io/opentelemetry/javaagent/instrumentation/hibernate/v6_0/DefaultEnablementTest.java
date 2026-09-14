/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;

class DefaultEnablementTest extends AbstractHibernateTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  @Test
  void defaultEnablement() {
    testing.runWithSpan(
        "parent",
        () -> {
          try (Session session = sessionFactory.openSession()) {
            session.find(Value.class, prepopulated.get(0).getId());
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
                      span.hasName("Session.find " + Value.class.getName())
                          .hasParent(trace.getSpan(0)),
                  span -> span.hasKind(CLIENT).hasParent(trace.getSpan(1))));
    }
  }
}
