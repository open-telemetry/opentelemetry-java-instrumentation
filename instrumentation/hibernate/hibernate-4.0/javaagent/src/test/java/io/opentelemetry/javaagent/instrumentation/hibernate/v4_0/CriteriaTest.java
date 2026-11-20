/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.HIBERNATE_SESSION_ID;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimental;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimentalSatisfies;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;

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
  @SuppressWarnings("deprecation") // createCriteria(Class) has been deprecated in v5
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
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Criteria." + methodName + " " + Value.class.getName())
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("select Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "h2database"),
                            equalTo(DB_NAMESPACE, "db1"),
                            satisfies(DB_QUERY_TEXT, val -> val.startsWith("select")),
                            equalTo(DB_QUERY_SUMMARY, "select Value")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                HIBERNATE_SESSION_ID,
                                experimental(
                                    trace.getSpan(1).getAttributes().get(HIBERNATE_SESSION_ID))))));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("list", (Consumer<Criteria>) Criteria::list),
        Arguments.of("uniqueResult", (Consumer<Criteria>) Criteria::uniqueResult),
        Arguments.of("scroll", (Consumer<Criteria>) Criteria::scroll));
  }
}
