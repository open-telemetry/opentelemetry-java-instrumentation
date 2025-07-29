/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.OperationNameUtil.getOperationNameForQuery;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v6_0.Hibernate6Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

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
import org.hibernate.query.CommonQueryContract;
import org.hibernate.query.Query;
import org.hibernate.query.spi.SqmQuery;

public class QueryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.query.CommonQueryContract");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.query.CommonQueryContract"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                // not instrumenting getSingleResult as it calls list that is instrumented and
                // we don't want to record the NoResultException that it throws
                namedOneOf(
                    "list",
                    "getResultList",
                    "stream",
                    "getResultStream",
                    "uniqueResult",
                    "getSingleResultOrNull",
                    "uniqueResultOptional",
                    "executeUpdate",
                    "scroll")),
        QueryInstrumentation.class.getName() + "$QueryMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HibernateOperationScope startMethod(@Advice.This CommonQueryContract query) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      String queryString = null;
      if (query instanceof Query) {
        queryString = ((Query<?>) query).getQueryString();
      }
      if (query instanceof SqmQuery) {
        try {
          queryString = ((SqmQuery) query).getSqmStatement().toHqlString();
        } catch (RuntimeException exception) {
          // ignore
        }
      }

      VirtualField<CommonQueryContract, SessionInfo> queryVirtualField =
          VirtualField.find(CommonQueryContract.class, SessionInfo.class);
      SessionInfo sessionInfo = queryVirtualField.get(query);

      Context parentContext = Java8BytecodeBridge.currentContext();
      HibernateOperation hibernateOperation =
          new HibernateOperation(getOperationNameForQuery(queryString), sessionInfo);

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable, @Advice.Enter HibernateOperationScope scope) {

      HibernateOperationScope.end(scope, throwable);
    }
  }
}
