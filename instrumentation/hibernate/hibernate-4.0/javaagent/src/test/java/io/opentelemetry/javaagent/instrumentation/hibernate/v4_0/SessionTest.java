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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.MappingException;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // 'lock' is a deprecated method in the Session class
class SessionTest extends AbstractHibernateTest {

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsHibernateAction")
  void testHibernateAction(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          parameter.sessionMethodTest.accept(session, prepopulated.get(0));
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session." + parameter.methodName + " " + Value.class.getName())
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

  private static Stream<Arguments> provideArgumentsHibernateAction() {
    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock",
                    (Session session, Value val) -> session.lock(val, LockMode.READ),
                    null))),
        Arguments.of(
            named(
                "lock with entity name",
                new Parameter(
                    "lock",
                    (Session session, Value val) ->
                        session.lock(Value.class.getName(), val, LockMode.READ),
                    null))),
        Arguments.of(
            named(
                "lock with null name",
                new Parameter(
                    "lock",
                    (Session session, Value val) -> session.lock(null, val, LockMode.READ),
                    null))),
        Arguments.of(
            named(
                "buildLockRequest",
                new Parameter(
                    "lock",
                    (Session session, Value val) ->
                        session.buildLockRequest(LockOptions.READ).lock(val),
                    null))),
        Arguments.of(named("refresh", new Parameter("refresh", Session::refresh, null))),
        Arguments.of(
            named(
                "refresh with entity name",
                new Parameter(
                    "refresh",
                    (Session session, Value val) -> session.refresh(Value.class.getName(), val),
                    null))),
        Arguments.of(
            named(
                "get with entity name",
                new Parameter(
                    "get",
                    (Session session, Value val) -> session.get(Value.class.getName(), val.getId()),
                    null))),
        Arguments.of(
            named(
                "get with entity class",
                new Parameter(
                    "get",
                    (Session session, Value val) -> session.get(Value.class, val.getId()),
                    null))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsHibernateActionStateless")
  void testHibernateActionStateless(Parameter parameter) {

    testing.runWithSpan(
        "parent",
        () -> {
          StatelessSession session = sessionFactory.openStatelessSession();
          session.beginTransaction();
          parameter.statelessSessionMethodTest.accept(session, prepopulated.get(0));
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session." + parameter.methodName + " " + Value.class.getName())
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

  private static Stream<Arguments> provideArgumentsHibernateActionStateless() {
    return Stream.of(
        Arguments.of(named("refresh", new Parameter("refresh", null, StatelessSession::refresh))),
        Arguments.of(
            named(
                "refresh with entity name",
                new Parameter(
                    "refresh",
                    null,
                    (StatelessSession session, Value val) ->
                        session.refresh(Value.class.getName(), val)))),
        Arguments.of(
            named(
                "get with entity name",
                new Parameter(
                    "get",
                    null,
                    (StatelessSession session, Value val) ->
                        session.get(Value.class.getName(), val.getId())))),
        Arguments.of(
            named(
                "get with entity class",
                new Parameter(
                    "get",
                    null,
                    (StatelessSession session, Value val) ->
                        session.get(Value.class, val.getId())))),
        Arguments.of(
            named(
                "insert",
                new Parameter(
                    "insert",
                    null,
                    (StatelessSession session, Value val) ->
                        session.insert(new Value("insert me"))))),
        Arguments.of(
            named(
                "insert with entity name",
                new Parameter(
                    "insert",
                    null,
                    (StatelessSession session, Value val) ->
                        session.insert(Value.class.getName(), new Value("insert me"))))),
        Arguments.of(
            named(
                "insert with null entity name",
                new Parameter(
                    "insert",
                    null,
                    (StatelessSession session, Value val) ->
                        session.insert(null, new Value("insert me"))))),
        Arguments.of(
            named(
                "update (StatelessSession)",
                new Parameter(
                    "update",
                    null,
                    (StatelessSession session, Value val) -> {
                      val.setName("New name");
                      session.update(val);
                    }))),
        Arguments.of(
            named(
                "update with entity name (StatelessSession)",
                new Parameter(
                    "update",
                    null,
                    (StatelessSession session, Value val) -> {
                      val.setName("New name");
                      session.update(Value.class.getName(), val);
                    }))),
        Arguments.of(
            named(
                "delete (Session)",
                new Parameter(
                    "delete",
                    null,
                    (StatelessSession session, Value val) -> {
                      session.delete(val);
                      prepopulated.remove(val);
                    }))),
        Arguments.of(
            named(
                "delete with entity name (Session)",
                new Parameter(
                    "delete",
                    null,
                    (StatelessSession session, Value val) -> {
                      session.delete(Value.class.getName(), val);
                      prepopulated.remove(val);
                    }))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsHibernateReplicate")
  void testHibernateReplicate(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          parameter.sessionMethodTest.accept(session, prepopulated.get(0));
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session." + parameter.methodName + " " + Value.class.getName())
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

  private static Stream<Arguments> provideArgumentsHibernateReplicate() {
    return Stream.of(
        Arguments.of(
            named(
                "replicate",
                new Parameter(
                    "replicate",
                    (Session session, Value val) -> {
                      Value replicated = new Value(val.getName() + " replicated");
                      replicated.setId(val.getId());
                      session.replicate(replicated, ReplicationMode.OVERWRITE);
                    },
                    null))),
        Arguments.of(
            named(
                "replicate by entity name",
                new Parameter(
                    "replicate",
                    (Session session, Value val) -> {
                      Value replicated = new Value(val.getName() + " replicated");
                      replicated.setId(val.getId());
                      session.replicate(
                          Value.class.getName(), replicated, ReplicationMode.OVERWRITE);
                    },
                    null))));
  }

  @Test
  void testHibernateFailedReplicate() {
    Throwable mappingException =
        testing.runWithSpan(
            "parent",
            () -> {
              Session session = sessionFactory.openSession();
              session.beginTransaction();

              Throwable exception =
                  catchThrowable(
                      () -> {
                        session.replicate(123L /* Not a valid entity */, ReplicationMode.OVERWRITE);
                      });

              session.getTransaction().commit();
              session.close();
              return exception;
            });

    assertThat(mappingException.getClass()).isEqualTo(MappingException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session.replicate java.lang.Long")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id"))))
                        .hasException(mappingException),
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
  @MethodSource("provideArgumentsHibernateCommitAction")
  void testHibernateCommitAction(Parameter parameter) {

    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          parameter.sessionMethodTest.accept(session, prepopulated.get(0));
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session." + parameter.methodName + " " + Value.class.getName())
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
                            equalTo(maybeStable(DB_SQL_TABLE), "Value"))));
  }

  private static Stream<Arguments> provideArgumentsHibernateCommitAction() {
    return Stream.of(
        Arguments.of(
            named(
                "save",
                new Parameter(
                    "save",
                    (Session session, Value val) -> {
                      session.save(new Value("Another value"));
                    },
                    null))),
        Arguments.of(
            named(
                "save with entity name",
                new Parameter(
                    "save",
                    (Session session, Value val) -> {
                      session.save(Value.class.getName(), new Value("Another value"));
                    },
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate save",
                new Parameter(
                    "saveOrUpdate",
                    (Session session, Value val) -> {
                      session.saveOrUpdate(new Value("Value"));
                    },
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate save with entity name",
                new Parameter(
                    "saveOrUpdate",
                    (Session session, Value val) -> {
                      session.saveOrUpdate(Value.class.getName(), new Value("Value"));
                    },
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate update with entity name",
                new Parameter(
                    "saveOrUpdate",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.saveOrUpdate(Value.class.getName(), val);
                    },
                    null))),
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    (Session session, Value val) -> {
                      session.merge(new Value("merge me in"));
                    },
                    null))),
        Arguments.of(
            named(
                "merge with entity name",
                new Parameter(
                    "merge",
                    (Session session, Value val) -> {
                      session.merge(Value.class.getName(), new Value("merge me in"));
                    },
                    null))),
        Arguments.of(
            named(
                "persist",
                new Parameter(
                    "persist",
                    (Session session, Value val) -> {
                      session.persist(new Value("merge me in"));
                    },
                    null))),
        Arguments.of(
            named(
                "persist with entity name",
                new Parameter(
                    "persist",
                    (Session session, Value val) -> {
                      session.persist(Value.class.getName(), new Value("merge me in"));
                    },
                    null))),
        Arguments.of(
            named(
                "persist with null entity name",
                new Parameter(
                    "persist",
                    (Session session, Value val) -> {
                      session.persist(null, new Value("merge me in"));
                    },
                    null))),
        Arguments.of(
            named(
                "update (Session)",
                new Parameter(
                    "update",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.update(val);
                    },
                    null))),
        Arguments.of(
            named(
                "update by entityName (Session)",
                new Parameter(
                    "update",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.update(Value.class.getName(), val);
                    },
                    null))),
        Arguments.of(
            named(
                "delete (Session)",
                new Parameter(
                    "delete",
                    (Session session, Value val) -> {
                      session.delete(val);
                      prepopulated.remove(val);
                    },
                    null))),
        Arguments.of(
            named(
                "delete by entityName (Session)",
                new Parameter(
                    "delete",
                    (Session session, Value val) -> {
                      session.delete(Value.class.getName(), val);
                      prepopulated.remove(val);
                    },
                    null))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @ParameterizedTest
  @MethodSource("provideArgumentsStateQuery")
  void testAttachesStateToQueryCreated(Consumer<Session> queryBuilder) {

    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          queryBuilder.accept(session);
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SELECT Value")
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

  private static Stream<Arguments> provideArgumentsStateQuery() {
    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                ((Consumer<Session>) session -> session.createQuery("from Value").list())),
            Arguments.of(
                named(
                    "getNamedQuery",
                    ((Consumer<Session>)
                        session -> session.getNamedQuery("TestNamedQuery").list())),
                Arguments.of(
                    named(
                        "createSQLQuery",
                        (Consumer<Session>)
                            session -> session.createSQLQuery("SELECT * FROM Value").list())))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testHibernateOverlappingSessions() {
    testing.runWithSpan(
        "overlapping Sessions",
        () -> {
          Session session1 = sessionFactory.openSession();
          session1.beginTransaction();

          StatelessSession session2 = sessionFactory.openStatelessSession();
          Session session3 = sessionFactory.openSession();

          Value value1 = new Value("Value 1");
          session1.save(value1);
          session2.insert(new Value("Value 2"));
          session3.save(new Value("Value 3"));
          session1.delete(value1);

          session2.close();
          session1.getTransaction().commit();
          session1.close();
          session3.close();
        });

    AtomicReference<String> sessionId1 = new AtomicReference<>();
    AtomicReference<String> sessionId2 = new AtomicReference<>();
    AtomicReference<String> sessionId3 = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("overlapping Sessions"),
                span -> {
                  span.hasName("Session.save " + Value.class.getName())
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class)));
                  sessionId1.set(
                      trace.getSpan(1).getAttributes().get(stringKey("hibernate.session_id")));
                },
                span -> {
                  span.hasName("Session.insert " + Value.class.getName())
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class)));
                  sessionId2.set(
                      trace.getSpan(2).getAttributes().get(stringKey("hibernate.session_id")));
                },
                span ->
                    span.hasName("INSERT db1.Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                stringAssert -> stringAssert.startsWith("insert")),
                            equalTo(maybeStable(DB_OPERATION), "INSERT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")),
                span -> {
                  span.hasName("Session.save " + Value.class.getName())
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class)));
                  sessionId3.set(
                      trace.getSpan(4).getAttributes().get(stringKey("hibernate.session_id")));
                },
                span ->
                    span.hasName("Session.delete " + Value.class.getName())
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
                    span.hasName("INSERT db1.Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(6))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                stringAssert -> stringAssert.startsWith("insert")),
                            equalTo(maybeStable(DB_OPERATION), "INSERT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value")),
                span ->
                    span.hasName("DELETE db1.Value")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(6))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("h2")),
                            equalTo(maybeStable(DB_NAME), "db1"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "h2:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                stringAssert -> stringAssert.startsWith("delete")),
                            equalTo(maybeStable(DB_OPERATION), "DELETE"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value"))));

    assertThat(sessionId1.get()).isNotEqualTo(sessionId2.get());
    assertThat(sessionId1.get()).isNotEqualTo(sessionId3.get());
    assertThat(sessionId2.get()).isNotEqualTo(sessionId3.get());
  }

  private static class Parameter {
    public final String methodName;
    public final BiConsumer<Session, Value> sessionMethodTest;
    public final BiConsumer<StatelessSession, Value> statelessSessionMethodTest;

    public Parameter(
        String methodName,
        BiConsumer<Session, Value> sessionMethodTest,
        BiConsumer<StatelessSession, Value> statelessSessionMethodTest) {
      this.methodName = methodName;
      this.sessionMethodTest = sessionMethodTest;
      this.statelessSessionMethodTest = statelessSessionMethodTest;
    }
  }
}
