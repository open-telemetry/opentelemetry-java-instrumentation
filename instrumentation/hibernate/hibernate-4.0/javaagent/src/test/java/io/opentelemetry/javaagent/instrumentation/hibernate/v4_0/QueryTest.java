/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.Query;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueryTest extends AbstractHibernateTest {

  @Test
  void testHibernateQueryExecuteUpdate() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          Query q = session.createQuery("update Value set name = :name");
          q.setParameter("name", "alyx");
          q.executeUpdate();
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("UPDATE Value")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
                            equalTo(SemanticAttributes.DB_NAME, "db1"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val -> val.isInstanceOf(String.class)),
                            satisfies(
                                SemanticAttributes.DB_OPERATION,
                                val -> val.isInstanceOf(String.class)),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(AttributeKey.stringKey("hibernate.session_id"))))));
  }

  @ParameterizedTest
  @MethodSource("providesArgumentsSingleCall")
  void testHibernateQuerySingleCall(Parameter parameter) {

    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          parameter.queryInteraction.accept(session);
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(parameter.expectedSpanName)
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
                            equalTo(SemanticAttributes.DB_NAME, "db1"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT, val -> val.startsWith("select")),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value"))));
  }

  @Test
  void testHibernateQueryIterate() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          Query query = session.createQuery("from Value");
          @SuppressWarnings("unchecked")
          Iterator<Value> iterator = query.iterate();
          while (iterator.hasNext()) {
            iterator.next();
          }
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT Value")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "h2"),
                            equalTo(SemanticAttributes.DB_NAME, "db1"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "h2:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT, val -> val.startsWith("select")),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(AttributeKey.stringKey("hibernate.session_id"))))));
  }

  private static Stream<Arguments> providesArgumentsSingleCall() {
    return Stream.of(
        Arguments.of(
            named(
                "query/list",
                new Parameter(
                    "SELECT Value",
                    sess -> {
                      Query q = sess.createQuery("from Value");
                      q.list();
                    }))),
        Arguments.of(
            named(
                "query/uniqueResult",
                new Parameter(
                    "SELECT Value",
                    sess -> {
                      Query q = sess.createQuery("from Value where id = :id");
                      q.setParameter("id", 1L);
                      q.uniqueResult();
                    }))),
        Arguments.of(
            named(
                "iterate",
                new Parameter(
                    "SELECT Value",
                    sess -> {
                      Query q = sess.createQuery("from Value");
                      q.iterate();
                    }))),
        Arguments.of(
            named(
                "query/scroll",
                new Parameter(
                    "SELECT Value",
                    sess -> {
                      Query q = sess.createQuery("from Value");
                      q.scroll();
                    }))));
  }

  private static class Parameter {
    public final String expectedSpanName;
    public final Consumer<Session> queryInteraction;

    public Parameter(String expectedSpanName, Consumer<Session> queryInteraction) {
      this.expectedSpanName = expectedSpanName;
      this.queryInteraction = queryInteraction;
    }
  }
}
