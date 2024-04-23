/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.junit.jupiter.api.Named.named;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SessionTest extends AbstractHibernateTest {
  @ParameterizedTest
  @MethodSource("provideArguments")
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
        trace -> sessionAssertion(trace, parameter.methodName, parameter.resource));
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsStateless")
  void testHibernateActionWithStatelessSession(Parameter parameter) {

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
        trace -> sessionAssertion(trace, parameter.methodName, parameter.resource));
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsStatelessAction")
  void testHibernateStatelessAction(Parameter parameter) {
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

  @ParameterizedTest
  @MethodSource("provideArgumentsReplicate")
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
                    assertSessionSpan(span, trace.getSpan(0), "Session.replicate java.lang.Long")
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
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
  @MethodSource("provideArgumentsCommitAction")
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

  @ParameterizedTest
  @MethodSource("provideArgumentsForQueryMethods")
  void testAttachesStateToQueryCreatedViaQueryMethods(Parameter parameter) {
    testing.runWithSpan(
        "parent",
        () -> {
          Session session = sessionFactory.openSession();
          session.beginTransaction();
          Query query = parameter.queryBuildMethod.apply(session);
          query.list();
          session.getTransaction().commit();
          session.close();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> assertSessionSpan(span, trace.getSpan(0), parameter.resource),
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

          Value value1 =
              new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 1");
          session1.save(value1);
          session2.insert(
              new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 2"));
          session3.save(
              new Value("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value 3"));
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
                        "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.insert io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"),
                span ->
                    assertSessionSpan(
                        span,
                        trace.getSpan(0),
                        "Session.save io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value"),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Session.delete io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span ->
                    assertSpanWithSessionId(
                        span,
                        trace.getSpan(0),
                        "Transaction.commit",
                        trace
                            .getSpan(1)
                            .getAttributes()
                            .get(AttributeKey.stringKey("hibernate.session_id"))),
                span -> assertClientSpan(span, trace.getSpan(5), "INSERT"),
                span -> assertClientSpan(span, trace.getSpan(5), "DELETE")));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            named(
                "lock",
                new Parameter(
                    "lock",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> session.lock(val, LockMode.READ),
                    null,
                    null))),
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    Session::refresh,
                    null,
                    null))),
        Arguments.of(
            named(
                "get",
                new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) ->
                        session.get(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                            val.getId()),
                    null,
                    null))));
  }

  private static Stream<Arguments> provideArgumentsStateless() {
    return Stream.of(
        Arguments.of(
            named(
                "refresh",
                new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    StatelessSession::refresh,
                    null))),
        Arguments.of(
            named(
                "get",
                new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    (StatelessSession session, Value val) ->
                        session.get(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                            val.getId()),
                    null))));
  }

  private static Stream<Arguments> provideArgumentsStatelessAction() {
    return Stream.of(
        Arguments.of(
            named(
                "insert",
                new Parameter(
                    "insert",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    (StatelessSession session, Value val) ->
                        session.insert(
                            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                            new Value("insert me")),
                    null))),
        Arguments.of(
            named(
                "get",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    (StatelessSession session, Value val) -> {
                      val.setName("New name");
                      session.update(val);
                    },
                    null))),
        Arguments.of(
            named(
                "update by entityName",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    (StatelessSession session, Value val) -> {
                      val.setName("New name");
                      session.update(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val);
                    },
                    null))),
        Arguments.of(
            named(
                "delete",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    StatelessSession::delete,
                    null))));
  }

  private static Stream<Arguments> provideArgumentsReplicate() {
    return Stream.of(
        Arguments.of(
            named(
                "replicate",
                new Parameter(
                    "replicate",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> {
                      Value replicated = new Value(val.getName() + " replicated");
                      replicated.setId(val.getId());
                      session.replicate(replicated, ReplicationMode.OVERWRITE);
                    },
                    null,
                    null))),
        Arguments.of(
            named(
                "replicate by entityName",
                new Parameter(
                    "replicate",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> {
                      Value replicated = new Value(val.getName() + " replicated");
                      replicated.setId(val.getId());
                      session.replicate(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                          replicated,
                          ReplicationMode.OVERWRITE);
                    },
                    null,
                    null))));
  }

  private static Stream<Arguments> provideArgumentsCommitAction() {
    return Stream.of(
        Arguments.of(
            named(
                "save",
                new Parameter(
                    "save",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> session.save(new Value("Another value")),
                    null,
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate save",
                new Parameter(
                    "saveOrUpdate",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) ->
                        session.saveOrUpdate(
                            new Value(
                                "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")),
                    null,
                    null))),
        Arguments.of(
            named(
                "saveOrUpdate update",
                new Parameter(
                    "saveOrUpdate",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.saveOrUpdate(val);
                    },
                    null,
                    null))),
        Arguments.of(
            named(
                "merge",
                new Parameter(
                    "merge",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> session.merge(new Value("merge me in")),
                    null,
                    null))),
        Arguments.of(
            named(
                "persist",
                new Parameter(
                    "persist",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> session.persist(new Value("merge me in")),
                    null,
                    null))),
        Arguments.of(
            named(
                "update (Session)",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.update(val);
                    },
                    null,
                    null))),
        Arguments.of(
            named(
                "update by entityName (Session)",
                new Parameter(
                    "update",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    (Session session, Value val) -> {
                      val.setName("New name");
                      session.update(
                          "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val);
                    },
                    null,
                    null))),
        Arguments.of(
            named(
                "delete (Session)",
                new Parameter(
                    "delete",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    Session::delete,
                    null,
                    null))));
  }

  private static Stream<Arguments> provideArgumentsForQueryMethods() {
    return Stream.of(
        Arguments.of(
            named(
                "createQuery",
                new Parameter(
                    "createQuery",
                    "SELECT io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    null,
                    (Session session) ->
                        session.createQuery(
                            "from io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value")))),
        Arguments.of(
            named(
                "getNamedQuery",
                new Parameter(
                    "getNamedQuery",
                    "SELECT Value",
                    null,
                    null,
                    (Session session) -> session.getNamedQuery("TestNamedQuery")))),
        Arguments.of(
            named(
                "createSQLQuery",
                new Parameter(
                    "createSQLQuery",
                    "SELECT Value",
                    null,
                    null,
                    (Session session) -> session.createSQLQuery("SELECT * FROM Value")))));
  }

  private static class Parameter {
    public final String methodName;
    public final String resource;
    public final BiConsumer<Session, Value> sessionMethodTest;
    public final BiConsumer<StatelessSession, Value> statelessSessionMethodTest;
    public final Function<Session, Query> queryBuildMethod;

    public Parameter(
        String methodName,
        String resource,
        BiConsumer<Session, Value> sessionMethodTest,
        BiConsumer<StatelessSession, Value> statelessSessionMethodTest,
        Function<Session, Query> queryBuildMethod) {
      this.methodName = methodName;
      this.resource = resource;
      this.sessionMethodTest = sessionMethodTest;
      this.statelessSessionMethodTest = statelessSessionMethodTest;
      this.queryBuildMethod = queryBuildMethod;
    }
  }

  static void sessionAssertion(TraceAssert trace, String methodName, String resource) {
    trace.hasSpansSatisfyingExactly(
        span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
        span -> assertSessionSpan(span, trace.getSpan(0), "Session." + methodName + " " + resource),
        span -> assertClientSpan(span, trace.getSpan(1), "SELECT"),
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
