/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.SCOPE_ONLY_METHODS;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.getEntityName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils.getSessionMethodSpanName;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionMethodUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;

public class SessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.SharedSessionContract");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.SharedSessionContract"));
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
                    "fireLock",
                    "refresh",
                    "insert",
                    "delete",
                    // Lazy-load methods.
                    "immediateLoad",
                    "internalLoad")),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");
    // Handle the non-generic 'get' separately.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("get").or(named("find")))
            .and(returns(Object.class))
            .and(takesArgument(0, String.class).or(takesArgument(0, Class.class))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current Span to the returned object using a ContextStore.
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
        @Advice.This SharedSessionContract session, @Advice.Thrown Throwable throwable) {

      ContextStore<SharedSessionContract, Context> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      Context sessionContext = contextStore.get(session);
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

  @SuppressWarnings("unused")
  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This SharedSessionContract session,
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

      ContextStore<SharedSessionContract, Context> contextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      Context sessionContext = contextStore.get(session);

      if (sessionContext == null) {
        return; // No state found. We aren't in a Session.
      }

      if (!SCOPE_ONLY_METHODS.contains(name)) {
        String entityName =
            getEntityName(descriptor, arg0, arg1, EntityNameUtil.bestGuessEntityName(session));
        spanContext =
            tracer().startSpan(sessionContext, getSessionMethodSpanName(name), entityName);
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
    public static void getQuery(
        @Advice.This SharedSessionContract session, @Advice.Return Query query) {

      ContextStore<SharedSessionContract, Context> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      ContextStore<Query, Context> queryContextStore =
          InstrumentationContext.get(Query.class, Context.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, queryContextStore, query);
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This SharedSessionContract session, @Advice.Return Transaction transaction) {

      ContextStore<SharedSessionContract, Context> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      ContextStore<Transaction, Context> transactionContextStore =
          InstrumentationContext.get(Transaction.class, Context.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, transactionContextStore, transaction);
    }
  }

  @SuppressWarnings("unused")
  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This SharedSessionContract session, @Advice.Return Criteria criteria) {

      ContextStore<SharedSessionContract, Context> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, Context.class);
      ContextStore<Criteria, Context> criteriaContextStore =
          InstrumentationContext.get(Criteria.class, Context.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, criteriaContextStore, criteria);
    }
  }
}
