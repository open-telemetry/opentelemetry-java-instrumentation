/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

class CriteriaTest extends AbstractHibernateTest {

    private static Stream<Arguments> provideArguments() {
      return Stream.of(
          Arguments.of("list", (Consumer<Criteria>) Criteria::list),
          Arguments.of("uniqueResult", (Consumer<Criteria>) Criteria::uniqueResult),
          Arguments.of("scroll",(Consumer<Criteria>) Criteria::scroll));
    }

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
                      span.hasName(
                              "Criteria."
                                  + methodName
                                  + " io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              satisfies(
                                  AttributeKey.stringKey("hibernate.session_id"),
                                  val -> val.isInstanceOf(String.class))),
                  span ->
                      span.hasName(
                              "SELECT db1.Value")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(1))
                          .hasAttributesSatisfyingExactly(
                              equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
                              equalTo(SemanticAttributes.DB_NAME, "db1"),
                              equalTo(SemanticAttributes.DB_USER, "sa"),
                              equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
                              satisfies(
                                  SemanticAttributes.DB_STATEMENT,
                                  stringAssert -> stringAssert.startsWith("select")),
                              equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                              equalTo(
                                  SemanticAttributes.DB_SQL_TABLE,
                                  "Value")),
                  span ->
                      span.hasName("Transaction.commit")
                          .hasKind(SpanKind.INTERNAL)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  AttributeKey.stringKey("hibernate.session_id"),
                                  Objects.requireNonNull(
                                      trace
                                          .getSpan(1)
                                          .getAttributes()
                                          .get(AttributeKey.stringKey("hibernate.session_id")))))));
      }
}
