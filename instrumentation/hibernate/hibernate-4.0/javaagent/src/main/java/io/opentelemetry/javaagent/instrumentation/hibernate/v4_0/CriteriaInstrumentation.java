/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.hibernate.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.SessionInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.Criteria;
import org.hibernate.internal.CriteriaImpl;

public class CriteriaInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.Criteria");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.Criteria"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(namedOneOf("list", "uniqueResult", "scroll")),
        CriteriaInstrumentation.class.getName() + "$CriteriaMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class CriteriaMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startMethod(
        @Advice.This Criteria criteria,
        @Advice.Origin("#m") String name,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelHibernateOperation") HibernateOperation hibernateOperation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(HibernateOperation.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      String entityName = null;
      if (criteria instanceof CriteriaImpl) {
        entityName = ((CriteriaImpl) criteria).getEntityOrClassName();
      }

      VirtualField<Criteria, SessionInfo> criteriaVirtualField =
          VirtualField.find(Criteria.class, SessionInfo.class);
      SessionInfo sessionInfo = criteriaVirtualField.get(criteria);

      Context parentContext = Java8BytecodeBridge.currentContext();
      hibernateOperation = new HibernateOperation("Criteria." + name, entityName, sessionInfo);
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
