/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v3_3.Hibernate3Singletons.instrumenter;
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
import org.hibernate.Criteria;
import org.hibernate.impl.CriteriaImpl;

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
    public static HibernateOperationScope startMethod(
        @Advice.This Criteria criteria, @Advice.Origin("#m") String name) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      String entityName = null;
      if (criteria instanceof CriteriaImpl) {
        entityName = ((CriteriaImpl) criteria).getEntityOrClassName();
      }

      VirtualField<Criteria, SessionInfo> criteriaVirtualField =
          VirtualField.find(Criteria.class, SessionInfo.class);
      SessionInfo sessionInfo = criteriaVirtualField.get(criteria);

      Context parentContext = Java8BytecodeBridge.currentContext();
      HibernateOperation hibernateOperation =
          new HibernateOperation("Criteria." + name, entityName, sessionInfo);

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable, @Advice.Enter HibernateOperationScope scope) {

      HibernateOperationScope.end(scope, throwable);
    }
  }
}
