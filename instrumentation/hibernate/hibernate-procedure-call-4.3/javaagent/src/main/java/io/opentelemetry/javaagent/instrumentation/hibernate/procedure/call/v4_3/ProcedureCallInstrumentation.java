/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.procedure.call.v4_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.hibernate.procedure.call.v4_3.Hibernate43Singletons.PROCEDURE_CALL_SESSION_INFO;
import static io.opentelemetry.javaagent.instrumentation.hibernate.procedure.call.v4_3.Hibernate43Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3.HibernateOperation;
import io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3.HibernateOperationScope;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.procedure.ProcedureCall;

class ProcedureCallInstrumentation implements TypeInstrumentation {

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
        named("getOutputs"), getClass().getName() + "$ProcedureCallMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcedureCallMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static HibernateOperationScope startMethod(
        @Advice.This ProcedureCall call, @Advice.Origin("#m") String name) {

      if (HibernateOperationScope.enterDepthSkipCheck()) {
        return null;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      HibernateOperation hibernateOperation =
          new HibernateOperation(
              "ProcedureCall." + name,
              call.getProcedureName(),
              PROCEDURE_CALL_SESSION_INFO.get(call));

      return HibernateOperationScope.start(hibernateOperation, parentContext, instrumenter());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void endMethod(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable HibernateOperationScope scope) {

      HibernateOperationScope.end(scope, throwable);
    }
  }
}
