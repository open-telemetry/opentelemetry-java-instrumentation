/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

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
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EntityManagerTest extends AbstractHibernateTest {
  static final EntityManagerFactory entityManagerFactory =
      Persistence.createEntityManagerFactory("test-pu");

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideHibernateActionParameters")
  void testHibernateAction(Parameter parameter) {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    EntityTransaction entityTransaction = entityManager.getTransaction();
    entityTransaction.begin();

    Value entity;
    if (parameter.attach) {
      entity =
          testing.runWithSpan(
              "setup",
              () -> {
                return entityManager.merge(prepopulated.get(0));
              });
      testing.clearData();
    } else {
      entity = prepopulated.get(0);
    }

    testing.runWithSpan(
        "parent",
        () -> {
          try {
            parameter.sessionMethodTest.accept(entityManager, entity);
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
          entityTransaction.commit();
          entityManager.close();
        });

    boolean isPersistTest = "persist".equals(parameter.methodName);

    testing.waitAndAssertTraces(
        trace -> {
          if (isPersistTest) {
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session." + parameter.methodName + " " + parameter.resource),
                span -> assertClientSpan(span, trace.getSpan(1), "SELECT db1.Value"),
                span ->
                    assertClientSpan(
                        span, !parameter.flushOnCommit ? trace.getSpan(1) : trace.getSpan(3)),
                span ->
                    assertTransactionCommitSpan(
                        span,
                        trace.getSpan(0),
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))));
          } else {
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session." + parameter.methodName + " " + parameter.resource),
                span ->
                    assertClientSpan(
                        span, !parameter.flushOnCommit ? trace.getSpan(1) : trace.getSpan(2)),
                span ->
                    assertTransactionCommitSpan(
                        span,
                        trace.getSpan(0),
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))));
          }
        });
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAttachesStateParameters")
  void testAttachesStateToQuery(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          EntityManager entityManager = entityManagerFactory.createEntityManager();
          EntityTransaction entityTransaction = entityManager.getTransaction();
          entityTransaction.begin();
          Query query = parameter.queryBuildMethod.apply(entityManager);
          query.getResultList();
          entityTransaction.commit();
          entityManager.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertSessionSpan(span, trace.getSpan(0), parameter.resource),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(SpanKind.CLIENT)
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
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")),
                span ->
                    assertTransactionCommitSpan(
                        span,
                        trace.getSpan(0),
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id")))));
  }

  @Test
  void testNoResultExceptionIgnored() {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    Query query = entityManager.createQuery("from Value where id = :id");
    query.setParameter("id", 1000);
    assertThatThrownBy(query::getSingleResult).isInstanceOf(NoResultException.class);
    entityManager.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + Value.class.getName())
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasEvents(emptyList()),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  private static Stream<Arguments> provideHibernateActionParameters() {
    List<BiConsumer<EntityManager, Value>> sessionMethodTests =
        Arrays.asList(
            (em, val) -> em.lock(val, LockModeType.PESSIMISTIC_READ),
            (em, val) -> em.refresh(val),
            (em, val) -> em.find(Value.class, val.getId()),
            (em, val) -> em.persist(new Value("insert me")),
            (em, val) -> {
              val.setName("New name");
              em.merge(val);
            },
            (em, val) -> em.remove(val));

    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    true,
                    false,
                    sessionMethodTests.get(0)))),
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    true,
                    false,
                    sessionMethodTests.get(1)))),
        Arguments.of(
            named(
                "find",
                new Parameter(
                    "find",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    false,
                    false,
                    sessionMethodTests.get(2)))),
        Arguments.of(
            named(
                "persist",
                new Parameter(
                    "persist",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    false,
                    true,
                    sessionMethodTests.get(3)))),
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    true,
                    true,
                    sessionMethodTests.get(4)))),
        Arguments.of(
            named(
                "remove",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    true,
                    true,
                    sessionMethodTests.get(5)))));
  }

  private static Stream<Arguments> provideAttachesStateParameters() {
    List<Function<EntityManager, Query>> queryBuildMethods =
        Arrays.asList(
            em -> em.createQuery("from Value"),
            em -> em.createNamedQuery("TestNamedQuery"),
            em -> em.createNativeQuery("SELECT * FROM Value"));

    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                new Parameter(
                    "createQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    queryBuildMethods.get(0)))),
        Arguments.of(
            named(
                "getNamedQuery",
                new Parameter(
                    "getNamedQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Value",
                    queryBuildMethods.get(1)))),
        Arguments.of(
            named(
                "createSQLQuery",
                new Parameter("createSQLQuery", "SELECT Value", queryBuildMethods.get(2)))));
  }

  private static class Parameter {

    Parameter(String methodName, String resource, Function<EntityManager, Query> queryBuildMethod) {
      this.methodName = methodName;
      this.resource = resource;
      this.attach = false;
      this.flushOnCommit = false;
      this.sessionMethodTest = null;
      this.queryBuildMethod = queryBuildMethod;
    }

    Parameter(
        String methodName,
        String resource,
        boolean attach,
        boolean flushOnCommit,
        BiConsumer<EntityManager, Value> sessionMethodTest) {
      this.methodName = methodName;
      this.resource = resource;
      this.attach = attach;
      this.flushOnCommit = flushOnCommit;
      this.sessionMethodTest = sessionMethodTest;
      this.queryBuildMethod = null;
    }

    public final String methodName;
    public final String resource;
    public final boolean attach;
    public final boolean flushOnCommit;
    public final BiConsumer<EntityManager, Value> sessionMethodTest;
    public final Function<EntityManager, Query> queryBuildMethod;
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  private static SpanDataAssert assertClientSpan(SpanDataAssert span, SpanData parent) {
    return span.hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
            equalTo(maybeStable(DB_NAME), "db1"),
            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "h2:mem:"),
            satisfies(maybeStable(DB_STATEMENT), val -> val.isInstanceOf(String.class)),
            satisfies(maybeStable(DB_OPERATION), val -> val.isInstanceOf(String.class)),
            equalTo(maybeStable(DB_SQL_TABLE), "Value"));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  private static SpanDataAssert assertClientSpan(
      SpanDataAssert span, SpanData parent, String spanName) {
    return span.hasName(spanName)
        .hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
            equalTo(maybeStable(DB_NAME), "db1"),
            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "h2:mem:"),
            satisfies(maybeStable(DB_STATEMENT), val -> val.isInstanceOf(String.class)),
            satisfies(maybeStable(DB_OPERATION), val -> val.isInstanceOf(String.class)),
            equalTo(maybeStable(DB_SQL_TABLE), "Value"));
  }

  private static SpanDataAssert assertSessionSpan(
      SpanDataAssert span, SpanData parent, String spanName) {
    return span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            satisfies(
                AttributeKey.stringKey("hibernate.session_id"),
                val -> val.isInstanceOf(String.class)));
  }

  private static SpanDataAssert assertTransactionCommitSpan(
      SpanDataAssert span, SpanData parent, String sessionId) {
    return span.hasName("Transaction.commit")
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(AttributeKey.stringKey("hibernate.session_id"), sessionId));
  }
}
