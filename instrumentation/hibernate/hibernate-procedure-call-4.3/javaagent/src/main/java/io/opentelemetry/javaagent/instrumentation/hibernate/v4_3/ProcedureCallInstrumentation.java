/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v4_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.v4_3.Hibernate43Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

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
import org.hibernate.procedure.ProcedureCall;

public class ProcedureCallInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.hibernate.procedure.ProcedureCall");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.hibernate.procedure.ProcedureCall"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("getOutputs")),
        ProcedureCallInstrumentation.class.getName() + "$ProcedureCallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcedureCallMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static HibernateOperationScope startMethod(
        @Advice.This ProcedureCall call, @Advice.Origin("#m") String name) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      VirtualField<ProcedureCall, SessionInfo> criteriaVirtualField =
          VirtualField.find(ProcedureCall.class, SessionInfo.class);
      SessionInfo sessionInfo = criteriaVirtualField.get(call);

      Context parentContext = Java8BytecodeBridge.currentContext();
      HibernateOperation hibernateOperation =
          new HibernateOperation("ProcedureCall." + name, call.getProcedureName(), sessionInfo);

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void endMethod(
        @Advice.Thrown Throwable throwable, @Advice.Enter HibernateOperationScope scope) {

      HibernateOperationScope.end(scope, throwable);
    }
  }
}
