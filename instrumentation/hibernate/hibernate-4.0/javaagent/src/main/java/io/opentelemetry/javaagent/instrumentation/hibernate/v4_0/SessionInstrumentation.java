/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getEntityName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getSessionMethodOperationName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
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
                    "delete")),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");
    // Handle the non-generic 'get' separately.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("get").or(named("find")))
            .and(returns(Object.class))
            .and(takesArgument(0, String.class).or(takesArgument(0, Class.class))),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current SessionInfo to the returned object using a VirtualField.
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
  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This SharedSessionContract session,
        @Advice.Origin("#m") String name,
        @Advice.Origin("#d") String descriptor,
        @Advice.Argument(0) Object arg0,
        @Advice.Argument(value = 1, optional = true) Object arg1,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHibernateOperation") HibernateOperation hibernateOperation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(HibernateOperation.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      VirtualField<SharedSessionContract, SessionInfo> virtualField =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);
      SessionInfo sessionInfo = virtualField.get(session);

      Context parentContext = Java8BytecodeBridge.currentContext();
      String entityName =
          getEntityName(descriptor, arg0, arg1, EntityNameUtil.bestGuessEntityName(session));
      hibernateOperation =
          new HibernateOperation(getSessionMethodOperationName(name), entityName, sessionInfo);
      if (!instrumenter().shouldStart(parentContext, hibernateOperation)) {
        return;
      }

      context = instrumenter().start(parentContext, hibernateOperation);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHibernateOperation") HibernateOperation hibernateOperation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        instrumenter().end(context, hibernateOperation, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This SharedSessionContract session, @Advice.Return Query query) {

      VirtualField<SharedSessionContract, SessionInfo> sessionVirtualField =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);
      VirtualField<Query, SessionInfo> queryVirtualField =
          VirtualField.find(Query.class, SessionInfo.class);

      queryVirtualField.set(query, sessionVirtualField.get(session));
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This SharedSessionContract session, @Advice.Return Transaction transaction) {

      VirtualField<SharedSessionContract, SessionInfo> sessionVirtualField =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);
      VirtualField<Transaction, SessionInfo> transactionVirtualField =
          VirtualField.find(Transaction.class, SessionInfo.class);

      transactionVirtualField.set(transaction, sessionVirtualField.get(session));
    }
  }

  @SuppressWarnings("unused")
  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This SharedSessionContract session, @Advice.Return Criteria criteria) {

      VirtualField<SharedSessionContract, SessionInfo> sessionVirtualField =
          VirtualField.find(SharedSessionContract.class, SessionInfo.class);
      VirtualField<Criteria, SessionInfo> criteriaVirtualField =
          VirtualField.find(Criteria.class, SessionInfo.class);

      criteriaVirtualField.set(criteria, sessionVirtualField.get(session));
    }
  }
}
