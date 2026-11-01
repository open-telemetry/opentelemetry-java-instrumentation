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
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueryTest extends AbstractHibernateTest {

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testHibernateQueryExecuteUpdateWithTransaction() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          session
              .createQuery("update Value set name = :name")
              .setParameter("name", "alyx")
              .executeUpdate();
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
                    span.hasName("update Value")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, "h2database"),
                            equalTo(DB_NAMESPACE, "db1"),
                            satisfies(DB_QUERY_TEXT, val -> val.isInstanceOf(String.class)),
                            satisfies(
                                DB_QUERY_SUMMARY,
                                val -> {
                                  val.isInstanceOf(String.class);
                                }),
                            satisfies(
                                DB_OPERATION_NAME,
                                val -> {
                                  val.isNull();
                                })),
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

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
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
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName(parameter.expectedSpanName)
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
                            satisfies(DB_QUERY_TEXT, val -> val.startsWith("select ")),
                            equalTo(DB_QUERY_SUMMARY, "select Value"))));
  }

  private static Stream<Arguments> providesArgumentsSingleCall() {
    return Stream.of(
        Arguments.of(
            named(
                "query/list",
                new Parameter("select Value", sess -> sess.createQuery("from Value").list()))),
        Arguments.of(
            named(
                "query/uniqueResult",
                new Parameter(
                    "select Value",
                    sess ->
                        sess.createQuery("from Value where id = :id")
                            .setParameter("id", 1L)
                            .uniqueResult()))),
        Arguments.of(
            named(
                "iterate",
                new Parameter("select Value", sess -> sess.createQuery("from Value").iterate()))),
        Arguments.of(
            named(
                "query/scroll",
                new Parameter("select Value", sess -> sess.createQuery("from Value").scroll()))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testHibernateQueryIterate() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          @SuppressWarnings("unchecked")
          Iterator<Value> iterator = session.createQuery("from Value").iterate();
          while (iterator.hasNext()) {
            iterator.next();
          }
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
                    span.hasName("select Value")
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
                            satisfies(DB_QUERY_TEXT, val -> val.startsWith("select ")),
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

  private static class Parameter {
    final String expectedSpanName;
    final Consumer<Session> queryInteraction;

    Parameter(String expectedSpanName, Consumer<Session> queryInteraction) {
      this.expectedSpanName = expectedSpanName;
      this.queryInteraction = queryInteraction;
    }
  }
}
