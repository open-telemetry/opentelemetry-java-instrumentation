/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.Attributes;
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
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("UPDATE Value")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT), val -> val.isInstanceOf(String.class)),
                            satisfies(
                                maybeStable(DB_OPERATION), val -> val.isInstanceOf(String.class)),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id"))))));
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
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName(parameter.expectedSpanName)
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value"))));
  }

  private static Stream<Arguments> providesArgumentsSingleCall() {
    return Stream.of(
        Arguments.of(
            named(
                "query/list",
                new Parameter("SELECT Value", sess -> sess.createQuery("from Value").list()))),
        Arguments.of(
            named(
                "query/uniqueResult",
                new Parameter(
                    "SELECT Value",
                    sess ->
                        sess.createQuery("from Value where id = :id")
                            .setParameter("id", 1L)
                            .uniqueResult()))),
        Arguments.of(
            named(
                "iterate",
                new Parameter("SELECT Value", sess -> sess.createQuery("from Value").iterate()))),
        Arguments.of(
            named(
                "query/scroll",
                new Parameter("SELECT Value", sess -> sess.createQuery("from Value").scroll()))));
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
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT Value")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id"))))));
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
