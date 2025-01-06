/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getEntityName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getSessionMethodOperationName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Hibernate3Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperationScope;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.Query;
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
    // Session synchronous methods we want to instrument.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(takesArgument(0, any()))
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
                    "delete")),
        SessionInstrumentation.class.getName() + "$SessionMethodAdvice");

    // Handle the non-generic 'get' separately.
    transformer.applyAdviceToMethod(
        isMethod().and(named("get")).and(returns(Object.class)).and(takesArgument(0, String.class)),
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
    public static HibernateOperationScope startMethod(
        @Advice.This Object session,
        @Advice.Origin("#m") String name,
        @Advice.Origin("#d") String descriptor,
        @Advice.Argument(0) Object arg0,
        @Advice.Argument(value = 1, optional = true) Object arg1) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      SessionInfo sessionInfo = SessionUtil.getSessionInfo(session);
      String entityName =
          getEntityName(descriptor, arg0, arg1, EntityNameUtil.bestGuessEntityName(session));
      HibernateOperation hibernateOperation =
          new HibernateOperation(getSessionMethodOperationName(name), entityName, sessionInfo);

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable, @Advice.Enter HibernateOperationScope enterScope) {

      HibernateOperationScope.end(enterScope, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(@Advice.This Object session, @Advice.Return Query query) {

      SessionInfo sessionInfo = SessionUtil.getSessionInfo(session);
      VirtualField<Query, SessionInfo> queryVirtualField =
          VirtualField.find(Query.class, SessionInfo.class);
      queryVirtualField.set(query, sessionInfo);
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This Object session, @Advice.Return Transaction transaction) {

      SessionInfo sessionInfo = SessionUtil.getSessionInfo(session);
      VirtualField<Transaction, SessionInfo> transactionVirtualField =
          VirtualField.find(Transaction.class, SessionInfo.class);
      transactionVirtualField.set(transaction, sessionInfo);
    }
  }

  @SuppressWarnings("unused")
  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(@Advice.This Object session, @Advice.Return Criteria criteria) {

      SessionInfo sessionInfo = SessionUtil.getSessionInfo(session);
      VirtualField<Criteria, SessionInfo> criteriaVirtualField =
          VirtualField.find(Criteria.class, SessionInfo.class);
      criteriaVirtualField.set(criteria, sessionInfo);
    }
  }
}
