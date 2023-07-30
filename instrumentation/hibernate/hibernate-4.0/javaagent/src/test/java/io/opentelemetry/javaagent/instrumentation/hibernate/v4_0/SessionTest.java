/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_EVENT_NAME;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.EXCEPTION_TYPE;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // 'lock' is a deprecated method in the Session class
class SessionTest extends AbstractHibernateTest {

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
            sessionAssertion(
                trace,
                parameter.methodName,
                "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"));
  }

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
            sessionAssertion(
                trace,
                parameter.methodName,
                "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"));
  }

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
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session."
                            + parameter.methodName
                            + " io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
                span -> assertClientSpan(span, trace.getSpan(1)),
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
            session.replicate(123L /* Not a valid entity */, ReplicationMode.OVERWRITE);
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(AttributeKey.stringKey("hibernate.session_id"))))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName(EXCEPTION_EVENT_NAME)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, "org.hibernate.MappingException"),
                                        equalTo(
                                            EXCEPTION_MESSAGE, "Unknown entity: java.lang.Long"),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class)))),
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
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session."
                            + parameter.methodName
                            + " io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
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

  @ParameterizedTest
  @MethodSource("provideArgumentsStateQuery")
  void testAttachesStateToQueryCreated(Function<Session, Query> queryBuilder) {

    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          Query query = queryBuilder.apply(session);
          query.list();
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertSessionSpan(span, trace.getSpan(0), "SELECT Value"),
                span -> assertClientSpan(span, trace.getSpan(1)),
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

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("overlapping Sessions"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.insert io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
                span -> assertClientSpan(span, trace.getSpan(2), "INSERT"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.delete io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value"),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span -> assertClientSpan(span, trace.getSpan(6), "INSERT"),
                span -> assertClientSpan(span, trace.getSpan(6), "DELETE")));
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
                        session.lock(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                            val,
                            LockMode.READ),
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
                    (Session session, Value val) ->
                        session.refresh(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val),
                    null))),
        Arguments.of(
            named(
                "get with entity name",
                new Parameter(
                    "get",
                    (Session session, Value val) ->
                        session.get(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                            val.getId()),
                    null))),
        Arguments.of(
            named(
                "get with entity class",
                new Parameter(
                    "get",
                    (Session session, Value val) -> session.get(Value.class, val.getId()),
                    null))));
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
                        session.refresh(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                            val)))),
        Arguments.of(
            named(
                "get with entity name",
                new Parameter(
                    "get",
                    null,
                    (StatelessSession session, Value val) ->
                        session.get(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                            val.getId())))),
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
                        session.insert(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                            new Value("insert me"))))),
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
                      session.update(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val);
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
                      session.delete(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val);
                      prepopulated.remove(val);
                    }))));
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
                "replicate",
                new Parameter(
                    "replicate",
                    (Session session, Value val) -> {
                      Value replicated = new Value(val.getName() + " replicated");
                      replicated.setId(val.getId());
                      session.replicate(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                          replicated,
                          ReplicationMode.OVERWRITE);
                    },
                    null))));
  }

  private static Stream<Arguments> provideArgumentsStateQuery() {
    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                (Function<Session, Query>) session -> session.createQuery("from Value"))),
        Arguments.of(
            named(
                "getNamedQuery",
                (Function<Session, Query>) session -> session.getNamedQuery("TestNamedQuery"))),
        Arguments.of(
            named(
                "createSQLQuery",
                (Function<Session, Query>)
                    session -> session.createSQLQuery("SELECT * FROM Value"))));
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
                      session.save(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                          new Value("Another value"));
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
                      session.saveOrUpdate(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                          new Value("Value"));
                    },
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate update with entity name",
                new Parameter(
                    "saveOrUpdate",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.saveOrUpdate(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val);
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
                      session.merge(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                          new Value("merge me in"));
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
                      session.persist(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value",
                          new Value("merge me in"));
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
                      session.update(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val);
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
                      session.delete(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Value", val);
                      prepopulated.remove(val);
                    },
                    null))));
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

  static void sessionAssertion(TraceAssert trace, String methodName, String resource) {
    trace.hasSpansSatisfyingExactly(
        span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
        span -> assertSessionSpan(span, trace.getSpan(0), "Session." + methodName + " " + resource),
        span -> assertClientSpan(span, trace.getSpan(1)),
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
}
