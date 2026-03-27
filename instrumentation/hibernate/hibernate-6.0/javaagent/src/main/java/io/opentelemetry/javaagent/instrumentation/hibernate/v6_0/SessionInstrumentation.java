/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getEntityName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getSessionMethodOperationName;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Hibernate6Singletons.COMMON_QUERY_CONTRACT_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Hibernate6Singletons.SHARED_SESSION_CONTRACT_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Hibernate6Singletons.TRANSACTION_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Hibernate6Singletons.instrumenter;
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
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;
import org.hibernate.query.CommonQueryContract;

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
                    "delete",
                    "remove",
                    "upsert")),
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
        returns(implementsInterface(named("org.hibernate.query.CommonQueryContract")))
            .or(named("org.hibernate.query.spi.QueryImplementor")),
        getClass().getName() + "$GetQueryAdvice");
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
        @Advice.This SharedSessionContract session, @Advice.Return Object queryObject) {
      if (!(queryObject instanceof CommonQueryContract)) {
        return;
      }
      CommonQueryContract query = (CommonQueryContract) queryObject;

      COMMON_QUERY_CONTRACT_SESSION_INFO.set(
          query, SHARED_SESSION_CONTRACT_SESSION_INFO.get(session));
    }
  }

  @SuppressWarnings("unused")
  public static class GetTransactionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getTransaction(
        @Advice.This SharedSessionContract session, @Advice.Return Transaction transaction) {

      TRANSACTION_SESSION_INFO.set(transaction, SHARED_SESSION_CONTRACT_SESSION_INFO.get(session));
    }
  }
}
