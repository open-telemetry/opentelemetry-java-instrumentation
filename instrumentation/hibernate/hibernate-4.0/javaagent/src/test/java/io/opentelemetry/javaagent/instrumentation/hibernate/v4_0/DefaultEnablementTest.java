/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

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
                  span -> span.hasName("Session.get " + Value.class.getName()),
                  span ->
                      span.hasName(
                          emitStableDatabaseSemconv() ? "select Value" : "SELECT db1.Value")));
    }
  }
}
