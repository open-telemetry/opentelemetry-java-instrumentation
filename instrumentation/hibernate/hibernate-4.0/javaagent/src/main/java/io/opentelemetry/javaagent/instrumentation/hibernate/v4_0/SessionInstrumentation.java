/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getEntityName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getSessionMethodOperationName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.CRITERIA_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.QUERY_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.SESSION_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.SHARED_SESSION_CONTRACT_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.TRANSACTION_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_0.Hibernate4Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
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
        takesArgument(0, any())
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
        getClass().getName() + "$SessionMethodAdvice");
    // Handle the non-generic 'get' separately.
    transformer.applyAdviceToMethod(
        namedOneOf("get", "find")
            .and(returns(Object.class))
            .and(takesArgument(0, String.class).or(takesArgument(0, Class.class))),
        getClass().getName() + "$SessionMethodAdvice");

    // These methods return some object that we want to instrument, and so the Advice will pin the
    // current SessionInfo to the returned object using a VirtualField.
    transformer.applyAdviceToMethod(
        namedOneOf("beginTransaction", "getTransaction")
            .and(returns(named("org.hibernate.Transaction"))),
        getClass().getName() + "$GetTransactionAdvice");

    transformer.applyAdviceToMethod(
        returns(implementsInterface(named("org.hibernate.Query"))),
        getClass().getName() + "$GetQueryAdvice");

    transformer.applyAdviceToMethod(
        returns(implementsInterface(named("org.hibernate.Criteria"))),
        getClass().getName() + "$GetCriteriaAdvice");
  }

  @SuppressWarnings("unused")
  public static class SessionMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HibernateOperationScope startMethod(
        @Advice.This SharedSessionContract session,
        @Advice.Origin("#m") String name,
        @Advice.Origin("#d") String descriptor,
        @Advice.Argument(0) Object arg0,
        @Advice.Argument(value = 1, optional = true) Object arg1) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      SessionInfo sessionInfo = SHARED_SESSION_CONTRACT_SESSION_INFO.get(session);

      Context parentContext = Java8BytecodeBridge.currentContext();
      String entityName =
          getEntityName(descriptor, arg0, arg1, EntityNameUtil.bestGuessEntityName(session));
      HibernateOperation hibernateOperation =
          new HibernateOperation(getSessionMethodOperationName(name), entityName, sessionInfo);

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable, @Advice.Enter HibernateOperationScope scope) {

      HibernateOperationScope.end(scope, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class GetQueryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getQuery(
        @Advice.This SharedSessionContract session, @Advice.Return Query query) {

      QUERY_SESSION_INFO.set(query, SHARED_SESSION_CONTRACT_SESSION_INFO.get(session));
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This SharedSessionContract session, @Advice.Return Transaction transaction) {

      TRANSACTION_SESSION_INFO.set(transaction, SESSION_SESSION_INFO.get(session));
    }
  }

  @SuppressWarnings("unused")
  public static class GetCriteriaAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getCriteria(
        @Advice.This SharedSessionContract session, @Advice.Return Criteria criteria) {

      CRITERIA_SESSION_INFO.set(criteria, SESSION_SESSION_INFO.get(session));
    }
  }
}
