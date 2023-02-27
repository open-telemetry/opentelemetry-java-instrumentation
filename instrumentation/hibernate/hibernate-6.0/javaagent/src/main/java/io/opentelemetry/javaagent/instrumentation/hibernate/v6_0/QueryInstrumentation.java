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
                namedOneOf(
                    "list",
                    "getResultList",
                    "stream",
                    "getResultStream",
                    "uniqueResult",
                    "getSingleResult",
                    "getSingleResultOrNull",
                    "uniqueResultOptional",
                    "executeUpdate",
                    "scroll")),
        QueryInstrumentation.class.getName() + "$QueryMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class QueryMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This CommonQueryContract query,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHibernateOperation") HibernateOperation hibernateOperation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(HibernateOperation.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
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
      hibernateOperation =
          new HibernateOperation(getOperationNameForQuery(queryString), sessionInfo);
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
}
