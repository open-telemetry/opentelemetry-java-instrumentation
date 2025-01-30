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
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;
import org.hibernate.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EntityManagerTest extends AbstractHibernateTest {

  static final EntityManagerFactory entityManagerFactory =
      Persistence.createEntityManagerFactory("test-pu");

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsHibernateActionParameters")
  void testHibernateActions(Parameter parameter) {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    EntityTransaction entityTransaction = entityManager.getTransaction();
    entityTransaction.begin();

    Value entity;
    if (parameter.attach) {
      entity = testing.runWithSpan("setup", () -> entityManager.merge(prepopulated.get(0)));
      testing.clearData();
    } else {
      entity = prepopulated.get(0);
    }

    String version = Version.getVersionString();
    boolean isHibernate4 = version.startsWith("4.");
    boolean isLatestDep = version.startsWith("5.0");
    String action;
    if ((isHibernate4 || isLatestDep) && "find".equals(parameter.methodName)) {
      action = "get";
    } else {
      action = parameter.methodName;
    }

    testing.runWithSpan(
        "parent",
        () -> {
          parameter.sessionMethodTest.accept(entityManager, entity);
          entityTransaction.commit();
          entityManager.close();
        });

    testing.waitAndAssertTraces(
        trace -> {
          if (parameter.flushOnCommit) {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("Session." + action + " " + Value.class.getName())
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
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
                                    .get(stringKey("hibernate.session_id")))),
                span ->
                    span.hasKind(CLIENT)
                        .hasParent(trace.getSpan(2))
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
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")));

          } else {
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("Session." + action + " " + Value.class.getName())
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
                                    .get(stringKey("hibernate.session_id")))));
          }
        });
  }

  private static Stream<Arguments> provideArgumentsHibernateActionParameters() {
    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock", true, false, (em, v) -> em.lock(v, LockModeType.PESSIMISTIC_READ)))),
        Arguments.of(
            named("refresh", new Parameter("refresh", true, false, EntityManager::refresh))),
        Arguments.of(
            named(
                "find",
                new Parameter("find", false, false, (em, v) -> em.find(Value.class, v.getId())))),
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    true,
                    true,
                    (em, v) -> {
                      v.setName("New name");
                      em.merge(v);
                    }))),
        Arguments.of(named("remove", new Parameter("delete", true, true, EntityManager::remove))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testHibernatePersist() {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    EntityTransaction entityTransaction = entityManager.getTransaction();
    entityTransaction.begin();

    testing.runWithSpan(
        "parent",
        () -> {
          entityManager.persist(new Value("insert me"));
          entityTransaction.commit();
          entityManager.close();
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
                    span.hasName("Session.persist " + Value.class.getName())
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0)),
                // persist test has an extra query for getting id of inserted element
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
                                    .get(stringKey("hibernate.session_id")))),
                span ->
                    span.hasKind(CLIENT)
                        .hasParent(trace.getSpan(3))
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
                            equalTo(maybeStable(DB_SQL_TABLE), "Value"))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsAttachesState")
  void testAttachesStateToQuery(Function<EntityManager, Query> queryBuildMethod) {
    testing.runWithSpan(
        "parent",
        () -> {
          EntityManager entityManager = entityManagerFactory.createEntityManager();
          EntityTransaction entityTransaction = entityManager.getTransaction();
          entityTransaction.begin();
          queryBuildMethod.apply(entityManager).getResultList();
          entityTransaction.commit();
          entityManager.close();
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

  @Test
  void testNoResultExceptionIgnored() {
    EntityManager entityManager = entityManagerFactory.createEntityManager();
    Query query = entityManager.createQuery("from Value where id = :id");
    query.setParameter("id", 1000L);
    assertThatThrownBy(query::getSingleResult).isInstanceOf(NoResultException.class);
    entityManager.close();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT Value")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasEvents(emptyList()),
                span ->
                    span.hasName("SELECT db1.Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))));
  }

  private static Stream<Arguments> provideArgumentsAttachesState() {
    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                (Function<EntityManager, Query>) em -> em.createQuery("from Value"))),
        Arguments.of(
            named(
                "getNamedQuery",
                (Function<EntityManager, Query>) em -> em.createNamedQuery("TestNamedQuery"))),
        Arguments.of(
            named(
                "createSQLQuery",
                (Function<EntityManager, Query>)
                    em -> em.createNativeQuery("SELECT * FROM Value"))));
  }

  private static class Parameter {
    public final String methodName;
    public final boolean attach;
    public final boolean flushOnCommit;
    public final BiConsumer<EntityManager, Value> sessionMethodTest;

    public Parameter(
        String methodName,
        boolean attach,
        boolean flushOnCommit,
        BiConsumer<EntityManager, Value> sessionMethodTest) {
      this.methodName = methodName;
      this.attach = attach;
      this.flushOnCommit = flushOnCommit;
      this.sessionMethodTest = sessionMethodTest;
    }
  }
}
