/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v7_0;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import jakarta.persistence.Timeout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.LockMode;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.UnknownEntityTypeException;
import org.hibernate.query.SelectionQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // testing instrumentation of deprecated class
class SessionTest extends AbstractHibernateTest {
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideHibernateActionParameters")
  void testHibernateAction(Parameter parameter) {
    Session session = sessionFactory.openSession();
    session.beginTransaction();

    Value value = session.merge(prepopulated.get(0));
    testing.waitForTraces(1);
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () -> {
          try {
            parameter.sessionMethodTest.accept(session, value);
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
          session.getTransaction().commit();
          session.close();
        });
    assertTraces(parameter, "refresh".equals(parameter.methodName));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideHibernateActionWithStatelessSessionParameters")
  void testHibernateActionStatelessSession(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          StatelessSession session = sessionFactory.openStatelessSession();
          session.beginTransaction();
          try {
            parameter.statelessSessionMethodTest.accept(session, prepopulated.get(0));
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
          session.getTransaction().commit();
          session.close();
        });
    assertTraces(parameter, true);
  }

  private static void assertTraces(Parameter parameter, boolean hasClientSpan) {
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent());
          assertions.add(
              span ->
                  assertSessionSpan(
                      span,
                      trace.getSpan(0),
                      "Session." + parameter.methodName + " " + parameter.resource));
          if (hasClientSpan) {
            assertions.add(span -> assertClientSpan(span, trace.getSpan(1)));
          }
          assertions.add(
              span ->
                  assertSpanWithSessionId(
                      span,
                      trace.getSpan(0),
                      "Transaction.commit",
                      trace
                          .getSpan(1)
                          .getAttributes()
                          .get(AttributeKey.stringKey("hibernate.session_id"))));

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideHibernateReplicateParameters")
  void testHibernateReplicate(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          try {
            parameter.sessionMethodTest.accept(session, prepopulated.get(0));
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
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
                        "Session." + parameter.methodName + " " + parameter.resource),
                span -> assertClientSpan(span, trace.getSpan(1), "SELECT"),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span -> assertClientSpan(span, trace.getSpan(3))));
  }

  @Test
  void testHibernateFailedReplicate() {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          try {
            session.replicate(
                Long.valueOf(123) /* Not a valid entity */, ReplicationMode.OVERWRITE);
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session.replicate java.lang.Long")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasException(new UnknownEntityTypeException("java.lang.Long"))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
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

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideHibernateCommitActionParameters")
  void testHibernateCommitAction(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          try {
            parameter.sessionMethodTest.accept(session, prepopulated.get(0));
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
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
                        "Session." + parameter.methodName + " " + parameter.resource),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span -> assertClientSpan(span, trace.getSpan(2))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("provideAttachesStateToQueryParameters")
  void testAttachesStateToQuery(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          SelectionQuery<?> query = parameter.queryBuildMethod.apply(session);
          query.list();
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertSessionSpan(span, trace.getSpan(0), parameter.resource),
                span ->
                    span.hasKind(SpanKind.CLIENT)
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
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id")))));
  }

  @Test
  void testHibernateOverlappingSessions() {
    AtomicReference<String> sessionId1 = new AtomicReference<>();
    AtomicReference<String> sessionId2 = new AtomicReference<>();
    AtomicReference<String> sessionId3 = new AtomicReference<>();

    testing.runWithSpan(
        "overlapping Sessions",
        () -> {
          Session session1 = sessionFactory.openSession();
          session1.beginTransaction();
          StatelessSession session2 = sessionFactory.openStatelessSession();
          Session session3 = sessionFactory.openSession();

          Value value1 = new Value("Value 1");
          session1.merge(value1);
          session2.insert(new Value("Value 2"));
          session3.merge(new Value("Value 3"));
          session1.remove(value1);

          session2.close();
          session1.getTransaction().commit();
          session1.close();
          session3.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("overlapping Sessions"),
                span -> {
                  assertSessionSpan(
                      span,
                      trace.getSpan(0),
                      "Session.merge io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value");
                  sessionId1.set(
                      trace
                          .getSpan(1)
                          .getAttributes()
                          .get(AttributeKey.stringKey("hibernate.session_id")));
                },
                span -> {
                  assertSessionSpan(
                      span,
                      trace.getSpan(0),
                      "Session.insert io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value");
                  sessionId2.set(
                      trace
                          .getSpan(2)
                          .getAttributes()
                          .get(AttributeKey.stringKey("hibernate.session_id")));
                },
                span -> assertClientSpan(span, trace.getSpan(2), "INSERT"),
                span -> {
                  assertSessionSpan(
                      span,
                      trace.getSpan(0),
                      "Session.merge io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value");
                  sessionId3.set(
                      trace
                          .getSpan(4)
                          .getAttributes()
                          .get(AttributeKey.stringKey("hibernate.session_id")));
                },
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Session.remove io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                        sessionId1.get()),
                span ->
                    assertSpanWithSessionId(
                        span, trace.getSpan(0), "Transaction.commit", sessionId1.get()),
                span -> assertClientSpan(span, trace.getSpan(6), "INSERT")));

    assertNotEquals(sessionId1.get(), sessionId2.get());
    assertNotEquals(sessionId2.get(), sessionId3.get());
    assertNotEquals(sessionId1.get(), sessionId3.get());
  }

  private static Stream<Arguments> provideHibernateActionParameters() {
    List<BiConsumer<Session, Value>> sessionMethodTests =
        Arrays.asList(
            (session, val) -> session.lock(val, LockMode.READ),
            (session, val) -> session.lock(val, LockMode.READ, Timeout.seconds(1)),
            (session, val) -> session.refresh(val),
            (session, val) -> session.find(Value.class, val.getId()));

    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(0),
                    null))),
        Arguments.of(
            named(
                "lock with options",
                new Parameter(
                    "lock",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(1),
                    null))),
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(2),
                    null))),
        Arguments.of(
            named(
                "find",
                new Parameter(
                    "find",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(3),
                    null))));
  }

  private static Stream<Arguments> provideHibernateActionWithStatelessSessionParameters() {
    List<BiConsumer<StatelessSession, Value>> statelessSessionMethodTests =
        Arrays.asList(
            (statelessSession, val) -> statelessSession.refresh(val),
            (statelessSession, val) ->
                statelessSession.refresh(
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value", val),
            (statelessSession, val) ->
                statelessSession.get(
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value", val.getId()),
            (statelessSession, val) -> statelessSession.get(Value.class, val.getId()),
            (statelessSession, val) -> statelessSession.insert(new Value("insert me")),
            (statelessSession, val) ->
                statelessSession.insert(
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    new Value("insert me")),
            (statelessSession, val) -> statelessSession.insert(null, new Value("insert me")),
            (statelessSession, val) -> {
              val.setName("New name");
              statelessSession.update(val);
            },
            (statelessSession, val) -> {
              val.setName("New name");
              statelessSession.update(
                  "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value", val);
            },
            (statelessSession, val) -> {
              statelessSession.delete(val);
              prepopulated.remove(val);
            },
            (statelessSession, val) -> {
              statelessSession.delete(
                  "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value", val);
              prepopulated.remove(val);
            });

    return Stream.of(
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(0)))),
        Arguments.of(
            named(
                "refresh with entity name",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(1)))),
        Arguments.of(
            named(
                "get with entity name",
                new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(2)))),
        Arguments.of(
            named(
                "get with entity class",
                new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(3)))),
        Arguments.of(
            named(
                "insert",
                new Parameter(
                    "insert",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(4)))),
        Arguments.of(
            named(
                "insert with entity name",
                new Parameter(
                    "insert",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(5)))),
        Arguments.of(
            named(
                "insert with null entity name",
                new Parameter(
                    "insert",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(6)))),
        Arguments.of(
            named(
                "update (StatelessSession)",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(7)))),
        Arguments.of(
            named(
                "update with entity name (StatelessSession)",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(8)))),
        Arguments.of(
            named(
                "delete (Session)",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(9)))),
        Arguments.of(
            named(
                "delete with entity name (Session)",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    null,
                    statelessSessionMethodTests.get(10)))));
  }

  private static Stream<Arguments> provideHibernateReplicateParameters() {
    List<BiConsumer<Session, Value>> sessionMethodTests =
        Arrays.asList(
            (session, val) -> {
              Value replicated = new Value(val.getName() + " replicated");
              replicated.setId(val.getId());
              session.replicate(replicated, ReplicationMode.OVERWRITE);
            },
            (session, val) -> {
              Value replicated = new Value(val.getName() + " replicated");
              replicated.setId(val.getId());
              session.replicate("Value", replicated, ReplicationMode.OVERWRITE);
            });

    return Stream.of(
        Arguments.of(
            named(
                "replicate",
                new Parameter(
                    "replicate",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(0),
                    null))),
        Arguments.of(
            named(
                "replicate by entityName",
                new Parameter("replicate", "Value", null, sessionMethodTests.get(1), null))));
  }

  private static Stream<Arguments> provideHibernateCommitActionParameters() {
    List<BiConsumer<Session, Value>> sessionMethodTests =
        Arrays.asList(
            (session, val) -> session.merge(new Value("merge me in")),
            (session, val) -> session.merge("Value", new Value("merge me in")),
            (session, val) -> session.persist(new Value("merge me in")),
            (session, val) -> session.persist("Value", new Value("merge me in")),
            (session, val) -> session.persist(null, new Value("merge me in")),
            (session, val) -> {
              session.remove(val);
              prepopulated.remove(val);
            });

    return Stream.of(
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(0),
                    null))),
        Arguments.of(
            named(
                "merge with entity name",
                new Parameter("merge", "Value", null, sessionMethodTests.get(1), null))),
        Arguments.of(
            named(
                "persist",
                new Parameter(
                    "persist",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(2),
                    null))),
        Arguments.of(
            named(
                "persist with entity name",
                new Parameter("persist", "Value", null, sessionMethodTests.get(3), null))),
        Arguments.of(
            named(
                "persist with null entity name",
                new Parameter(
                    "persist",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(4),
                    null))),
        Arguments.of(
            named(
                "remove",
                new Parameter(
                    "remove",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    null,
                    sessionMethodTests.get(5),
                    null))));
  }

  private static Stream<Arguments> provideAttachesStateToQueryParameters() {
    List<Function<Session, SelectionQuery<?>>> queryBuildMethods =
        Arrays.asList(
            session -> session.createQuery("from Value"),
            session -> session.getNamedQuery("TestNamedQuery"),
            session -> session.createNativeQuery("SELECT * FROM Value"),
            session -> session.createSelectionQuery("from Value"));

    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                new Parameter(
                    "createQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    queryBuildMethods.get(0),
                    null,
                    null))),
        Arguments.of(
            named(
                "getNamedQuery",
                new Parameter(
                    "getNamedQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    queryBuildMethods.get(1),
                    null,
                    null))),
        Arguments.of(
            named(
                "createNativeQuery",
                new Parameter(
                    "createNativeQuery", "SELECT Value", queryBuildMethods.get(2), null, null))),
        Arguments.of(
            named(
                "createSelectionQuery",
                new Parameter(
                    "createSelectionQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v7_0.Value",
                    queryBuildMethods.get(3),
                    null,
                    null))));
  }

  private static class Parameter {
    final String methodName;
    final String resource;
    final Function<Session, SelectionQuery<?>> queryBuildMethod;
    final BiConsumer<Session, Value> sessionMethodTest;
    final BiConsumer<StatelessSession, Value> statelessSessionMethodTest;

    Parameter(
        String methodName,
        String resource,
        Function<Session, SelectionQuery<?>> queryBuildMethod,
        BiConsumer<Session, Value> sessionMethodTest,
        BiConsumer<StatelessSession, Value> statelessSessionMethodTest) {
      this.methodName = methodName;
      this.resource = resource;
      this.sessionMethodTest = sessionMethodTest;
      this.queryBuildMethod = queryBuildMethod;
      this.statelessSessionMethodTest = statelessSessionMethodTest;
    }
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

  private static SpanDataAssert assertSpanWithSessionId(
      SpanDataAssert span, SpanData parent, String spanName, String sessionId) {
    return span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(AttributeKey.stringKey("hibernate.session_id"), sessionId));
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
      SpanDataAssert span, SpanData parent, String verb) {
    return span.hasName(verb.concat(" db1.Value"))
        .hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
            equalTo(maybeStable(DB_NAME), "db1"),
            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
            equalTo(DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "h2:mem:"),
            satisfies(
                maybeStable(DB_STATEMENT),
                stringAssert -> stringAssert.startsWith(verb.toLowerCase(Locale.ROOT))),
            equalTo(maybeStable(DB_OPERATION), verb),
            equalTo(maybeStable(DB_SQL_TABLE), "Value"));
  }
}
