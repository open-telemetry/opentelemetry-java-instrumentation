/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;

public class SessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.Session", "org.hibernate.StatelessSession");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(
        namedOneOf("org.hibernate.Session", "org.hibernate.StatelessSession"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(named("close")).and(takesArguments(0)),
        SessionInstrumentation.class.getName() + "$SessionCloseAdvice");

    // Session synchronous methods we want to instrument.
    transformers.put(
        isMethod()
            .and(
                namedOneOf(
                    "save",
                    "replicate",
                    "saveOrUpdate",
                    "update",
                    "merge",
                    "persist",
                    "lock",
                    "refresh",
                    "insert",
                    "delete",
                    // Lazy-load methods.
                    "immediateLoad",
                    "internalLoad")),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // Handle the non-generic 'get' separately.
    transformers.put(
        isMethod()
            .and(named("get"))
            .and(returns(named("java.lang.Object")))
            .and(takesArgument(0, named("java.lang.String"))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a ContextStore.
    transformers.put(
        isMethod()
            .and(namedOneOf("beginTransaction", "getTransaction"))
            .and(returns(named("org.hibernate.Transaction"))),
        SessionInstrumentation.class.getName() + "$GetTransactionAdvice");

    transformers.put(
        isMethod().and(returns(hasInterface(named("org.hibernate.Query")))),
        SessionInstrumentation.class.getName() + "$GetQueryAdvice");

    transformers.put(
        isMethod().and(returns(hasInterface(named("org.hibernate.Criteria")))),
        SessionInstrumentation.class.getName() + "$GetCriteriaAdvice");

    return transformers;
  }

  public static class SessionCloseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This Object session, @Advice.Thrown Throwable throwable) {

      Context sessionContext = null;
      if (session instanceof Session) {
        ContextStore<Session, Context> contextStore =
            InstrumentationContext.get(Session.class, Context.class);
        sessionContext = contextStore.get((Session) session);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> contextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        sessionContext = contextStore.get((StatelessSession) session);
      }

      if (sessionContext == null) {
        return;
      }
      if (throwable != null) {
        tracer().endExceptionally(sessionContext, throwable);
      } else {
        tracer().end(sessionContext);
      }
    }
  }

  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This Object session,
        @Advice.Origin("#m") String name,
        @Advice.Argument(0) Object entity,
        @Advice.Local("otelContext") Context spanContext,
        @Advice.Local("otelScope") Scope scope) {

      Context sessionContext = null;
      if (session instanceof Session) {
        ContextStore<Session, Context> contextStore =
            InstrumentationContext.get(Session.class, Context.class);
        sessionContext = contextStore.get((Session) session);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> contextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        sessionContext = contextStore.get((StatelessSession) session);
      }

      if (sessionContext == null) {
        return; // No state found. We aren't in a Session.
      }

      if (CallDepthThreadLocalMap.incrementCallDepth(SessionMethodUtils.class) > 0) {
        return; // This method call is being traced already.
      }

      if (!SCOPE_ONLY_METHODS.contains(name)) {
        spanContext = tracer().startSpan(sessionContext, "Session." + name, entity);
        scope = spanContext.makeCurrent();
      } else {
        scope = sessionContext.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returned,
        @Advice.Origin("#m") String name,
        @Advice.Local("otelContext") Context spanContext,
        @Advice.Local("otelScope") Scope scope) {

      if (scope != null) {
        scope.close();
        SessionMethodUtils.end(spanContext, throwable, "Session." + name, returned);
      }
    }
  }

  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(@Advice.This Object session, @Advice.Return Query query) {

      ContextStore<Query, Context> queryContextStore =
          InstrumentationContext.get(Query.class, Context.class);
      if (session instanceof Session) {
        ContextStore<Session, Context> sessionContextStore =
            InstrumentationContext.get(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (Session) session, queryContextStore, query);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> sessionContextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (StatelessSession) session, queryContextStore, query);
      }
    }
  }

  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This Object session, @Advice.Return Transaction transaction) {

      ContextStore<Transaction, Context> transactionContextStore =
          InstrumentationContext.get(Transaction.class, Context.class);

      if (session instanceof Session) {
        ContextStore<Session, Context> sessionContextStore =
            InstrumentationContext.get(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (Session) session, transactionContextStore, transaction);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> sessionContextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (StatelessSession) session, transactionContextStore, transaction);
      }
    }
  }

  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(@Advice.This Object session, @Advice.Return Criteria criteria) {

      ContextStore<Criteria, Context> criteriaContextStore =
          InstrumentationContext.get(Criteria.class, Context.class);
      if (session instanceof Session) {
        ContextStore<Session, Context> sessionContextStore =
            InstrumentationContext.get(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (Session) session, criteriaContextStore, criteria);
      } else if (session instanceof StatelessSession) {
        ContextStore<StatelessSession, Context> sessionContextStore =
            InstrumentationContext.get(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionContextStore, (StatelessSession) session, criteriaContextStore, criteria);
      }
    }
  }
}
