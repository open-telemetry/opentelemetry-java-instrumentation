/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.getEntityName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
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
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("close")).and(takesArguments(0)),
        SessionInstrumentation.class.getName() + "$SessionCloseAdvice");

    // Session synchronous methods we want to instrument.
    transformer.applyAdviceToMethod(
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
    transformer.applyAdviceToMethod(
        isMethod().and(named("get")).and(returns(Object.class)).and(takesArgument(0, String.class)),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a VirtualField.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("beginTransaction", "getTransaction"))
            .and(returns(named("org.hibernate.Transaction"))),
        SessionInstrumentation.class.getName() + "$GetTransactionAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(returns(implementsInterface(named("org.hibernate.Query")))),
        SessionInstrumentation.class.getName() + "$GetQueryAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(returns(implementsInterface(named("org.hibernate.Criteria")))),
        SessionInstrumentation.class.getName() + "$GetCriteriaAdvice");
  }

  @SuppressWarnings("unused")
  public static class SessionCloseAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeSession(
        @Advice.This Object session, @Advice.Thrown Throwable throwable) {

      Context sessionContext = null;
      if (session instanceof Session) {
        VirtualField<Session, Context> virtualField =
            VirtualField.find(Session.class, Context.class);
        sessionContext = virtualField.get((Session) session);
      } else if (session instanceof StatelessSession) {
        VirtualField<StatelessSession, Context> virtualField =
            VirtualField.find(StatelessSession.class, Context.class);
        sessionContext = virtualField.get((StatelessSession) session);
      }

      if (sessionContext == null) {
        return;
      }
      instrumenter().end(sessionContext, null, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This Object session,
        @Advice.Origin("#m") String name,
        @Advice.Origin("#d") String descriptor,
        @Advice.Argument(0) Object arg0,
        @Advice.Argument(value = 1, optional = true) Object arg1,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context spanContext,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(SessionMethodUtils.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context sessionContext = null;
      if (session instanceof Session) {
        VirtualField<Session, Context> virtualField =
            VirtualField.find(Session.class, Context.class);
        sessionContext = virtualField.get((Session) session);
      } else if (session instanceof StatelessSession) {
        VirtualField<StatelessSession, Context> virtualField =
            VirtualField.find(StatelessSession.class, Context.class);
        sessionContext = virtualField.get((StatelessSession) session);
      }

      if (sessionContext == null) {
        return; // No state found. We aren't in a Session.
      }

      if (!SCOPE_ONLY_METHODS.contains(name)) {
        String entityName =
            getEntityName(descriptor, arg0, arg1, EntityNameUtil.bestGuessEntityName(session));
        spanContext =
            SessionMethodUtils.startSpanFrom(sessionContext, "Session." + name, entityName);
        scope = spanContext.makeCurrent();
      } else {
        scope = sessionContext.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelContext") Context spanContext,
        @Advice.Local("otelScope") Scope scope) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        SessionMethodUtils.end(spanContext, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(@Advice.This Object session, @Advice.Return Query query) {

      VirtualField<Query, Context> queryVirtualField =
          VirtualField.find(Query.class, Context.class);
      if (session instanceof Session) {
        VirtualField<Session, Context> sessionVirtualField =
            VirtualField.find(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (Session) session, queryVirtualField, query);
      } else if (session instanceof StatelessSession) {
        VirtualField<StatelessSession, Context> sessionVirtualField =
            VirtualField.find(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (StatelessSession) session, queryVirtualField, query);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This Object session, @Advice.Return Transaction transaction) {

      VirtualField<Transaction, Context> transactionVirtualField =
          VirtualField.find(Transaction.class, Context.class);

      if (session instanceof Session) {
        VirtualField<Session, Context> sessionVirtualField =
            VirtualField.find(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (Session) session, transactionVirtualField, transaction);
      } else if (session instanceof StatelessSession) {
        VirtualField<StatelessSession, Context> sessionVirtualField =
            VirtualField.find(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (StatelessSession) session, transactionVirtualField, transaction);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(@Advice.This Object session, @Advice.Return Criteria criteria) {

      VirtualField<Criteria, Context> criteriaVirtualField =
          VirtualField.find(Criteria.class, Context.class);
      if (session instanceof Session) {
        VirtualField<Session, Context> sessionVirtualField =
            VirtualField.find(Session.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (Session) session, criteriaVirtualField, criteria);
      } else if (session instanceof StatelessSession) {
        VirtualField<StatelessSession, Context> sessionVirtualField =
            VirtualField.find(StatelessSession.class, Context.class);
        SessionMethodUtils.attachSpanFromStore(
            sessionVirtualField, (StatelessSession) session, criteriaVirtualField, criteria);
      }
    }
  }
}
