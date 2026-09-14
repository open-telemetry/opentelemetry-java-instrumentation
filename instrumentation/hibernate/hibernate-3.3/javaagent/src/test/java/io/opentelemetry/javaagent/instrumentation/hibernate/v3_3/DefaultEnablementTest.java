/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

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
          Session session = sessionFactory.openSession();
          try {
            session.get(Value.class, prepopulated.get(0).getId());
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
                      assertSessionSpan(
                          span,
                          trace.getSpan(0),
                          "Session.get "
                              + "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"),
                  span ->
                      assertClientSpan(
                          span,
                          trace.getSpan(1),
                          emitStableDatabaseSemconv() ? "select" : "SELECT")));
    }
  }
}
