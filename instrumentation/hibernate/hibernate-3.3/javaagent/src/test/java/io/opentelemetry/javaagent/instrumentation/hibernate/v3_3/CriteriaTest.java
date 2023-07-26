/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CriteriaTest extends AbstractHibernateTest {

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testCriteria(String methodName, Consumer<Criteria> interaction) {

    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          Criteria criteria =
              session
                  .createCriteria(Value.class)
                  .add(Restrictions.like("name", "Hello"))
                  .addOrder(Order.desc("name"));
          interaction.accept(criteria);
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Criteria."
                            + methodName
                            + " io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"),
                span -> assertClientSpan(span, trace.getSpan(1), "SELECT"),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id")))));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("list", (Consumer<Criteria>) Criteria::list),
        Arguments.of("uniqueResult", (Consumer<Criteria>) Criteria::uniqueResult),
        Arguments.of("scroll", (Consumer<Criteria>) Criteria::scroll));
  }
}
