package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Named.named;

public class SessionTest extends AbstractHibernateTest {


  @ParameterizedTest
  @MethodSource("provideArguments")
  void testHibernateAction(Parameter parameter) {

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
        }
    );

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
          try {
            parameter.statelessSessionMethodTest.accept(session, prepopulated.get(0));
          } catch (RuntimeException e) {
            // We expected this, we should see the error field set on the span.
          }
          session.getTransaction().commit();
          session.close();
        }
    );

    testing.waitAndAssertTraces(
        trace -> sessionAssertion(trace, parameter.methodName, parameter.resource));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(named("lock", new Parameter(
            "lock",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            (Session session, Value val) ->  session.lock(val, LockMode.READ),
                null))),
        Arguments.of(named("refresh", new Parameter(
            "refresh",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            Session::refresh,
            null))),
        Arguments.of(named("get", new Parameter(
                "get",
                "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            (Session session, Value val) ->  session.get("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val.getId()),
                null)))
        );
  }

  private static Stream<Arguments> provideArgumentsStateless() {
    return Stream.of(
            Arguments.of(named("refresh", new Parameter(
                    "refresh",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                    null,
                    StatelessSession::refresh))),
                Arguments.of(named("get", new Parameter(
                    "get",
                    "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
                   null,
                    (StatelessSession session, Value val) ->  session.get("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val.getId())))
                ));
  }

  @ParameterizedTest
  @MethodSource("provideArgumentsStatelessAction")
  void testHibernateStatelessAction(Parameter parameter) {
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
        }
    );

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> assertSessionSpan(span, trace.getSpan(0), "Session."+parameter.methodName+" "+parameter.resource),
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

  private static Stream<Arguments> provideArgumentsStatelessAction() {
    return Stream.of(
        Arguments.of(named("insert", new Parameter(
            "insert",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            null,
                (StatelessSession session, Value val) -> session.insert("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", new Value("insert me"))))),
        Arguments.of(named("get", new Parameter(
            "update",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            null,
            (StatelessSession session, Value val) -> {
              val.setName("New name");
              session.update(val);
            }))),
        Arguments.of(named("update by entityName", new Parameter(
            "update",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            null,
            (StatelessSession session, Value val) -> {
              val.setName("New name");
              session.update("io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value", val);
            }))),
        Arguments.of(named("delete", new Parameter(
            "delete",
            "io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Value",
            null,
            StatelessSession::delete)))
        );
  }

  private static class Parameter {
    public final String methodName;
    public final String resource;
    public final BiConsumer<Session, Value> sessionMethodTest;

    public final BiConsumer<StatelessSession, Value> statelessSessionMethodTest;

    public Parameter(
        String methodName,
        String resource,
        BiConsumer<Session, Value> sessionMethodTest,
        BiConsumer<StatelessSession, Value> statelessSessionMethodTest) {
      this.methodName = methodName;
      this.resource = resource;
      this.sessionMethodTest = sessionMethodTest;
      this.statelessSessionMethodTest = statelessSessionMethodTest;
    }
  }

  static void sessionAssertion(TraceAssert trace, String methodName, String resource) {
      trace.hasSpansSatisfyingExactly(
          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
          span -> assertSessionSpan(span, trace.getSpan(0), "Session."+methodName+" "+resource),
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
