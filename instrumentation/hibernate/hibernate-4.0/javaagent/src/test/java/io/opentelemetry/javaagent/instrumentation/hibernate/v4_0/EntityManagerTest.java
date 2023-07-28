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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;
import javax.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EntityManagerTest extends AbstractHibernateTest {

  static final EntityManagerFactory entityManagerFactory =
      Persistence.createEntityManagerFactory("test-pu");

  @Test
  public void testHibernatePersist() {
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
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(
                            "Session.persist io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                // persist test has an extra query for getting id of inserted element
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
                                    .get(AttributeKey.stringKey("hibernate.session_id")))),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(3))));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideArgumentsHibernateActionParameters")
  public void testHibernateActions(Parameter parameter) {
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

    String action;
    if ("find".equals(parameter.methodName)) {
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
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span, trace.getSpan(0), "Session." + action + " " + parameter.resource),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(2))
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
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Value")));

          } else {
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span, trace.getSpan(0), "Session." + action + " " + parameter.resource),
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
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))));
          }
        });
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideArgumentsQueryState")
  public void testQueryState(Function<EntityManager, Query> queryBuildMethod) {
    testing.runWithSpan(
        "parent",
        () -> {
          EntityManager entityManager = entityManagerFactory.createEntityManager();
          EntityTransaction entityTransaction = entityManager.getTransaction();
          entityTransaction.begin();
          Query query = queryBuildMethod.apply(entityManager);
          query.getResultList();
          entityTransaction.commit();
          entityManager.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertSessionSpan(span, trace.getSpan(0), "SELECT Value"),
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
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id")))));
  }

  private static Stream<Arguments> provideArgumentsQueryState() {
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

  private static Stream<Arguments> provideArgumentsHibernateActionParameters() {
    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    true,
                    false,
                    (em, v) -> em.lock(v, LockModeType.PESSIMISTIC_READ)))),
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    true,
                    false,
                    EntityManager::refresh))),
        Arguments.of(
            named(
                "find",
                new Parameter(
                    "find",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    false,
                    false,
                    (em, v) -> em.find(Value.class, v.getId())))),
        Arguments.of(
            named(
                "get",
                new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    false,
                    false,
                    (em, v) -> em.find(Value.class, v.getId())))),
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    true,
                    true,
                    (em, v) -> {
                      v.setName("New name");
                      em.merge(v);
                    }))),
        Arguments.of(
            named(
                "remove",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                    true,
                    true,
                    EntityManager::remove))));
  }

  private static class Parameter {
    public final String methodName;
    public final String resource;
    public final boolean attach;
    public final boolean flushOnCommit;
    public final BiConsumer<EntityManager, Value> sessionMethodTest;

    public Parameter(
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
    }
  }
}
