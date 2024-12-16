/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior.CamundaBehaviorSingletons.getInstumenter;
import static io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior.CamundaBehaviorSingletons.getOpentelemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.camunda.v7_0.behavior.CamundaActivityExecutionGetter;
import io.opentelemetry.instrumentation.camunda.v7_0.common.CamundaCommonRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Optional;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;

public class CamundaTaskActivityBehaviorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.camunda.bpm.engine.impl.bpmn.behavior.TaskActivityBehavior");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.camunda.bpm.engine.impl.bpmn.behavior.TaskActivityBehavior"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        ElementMatchers.isMethod().and(ElementMatchers.named("performExecution")),
        this.getClass().getName() + "$CamundaTaskActivityBehaviorAdvice");
  }

  @SuppressWarnings("unused")
  public static class CamundaTaskActivityBehaviorAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingEnter(
        @Advice.Argument(0) ActivityExecution execution,
        @Advice.Local("request") CamundaCommonRequest request,
        @Advice.Local("otelParentScope") Scope parentScope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (execution == null) {
        return;
      }

      request = new CamundaCommonRequest();
      request.setProcessDefinitionId(Optional.ofNullable(execution.getProcessDefinitionId()));
      request.setProcessInstanceId(Optional.ofNullable(execution.getProcessInstanceId()));
      request.setActivityId(Optional.ofNullable(execution.getCurrentActivityId()));
      request.setActivityName(Optional.ofNullable(execution.getCurrentActivityName()));

      Context parentContext =
          getOpentelemetry()
              .getPropagators()
              .getTextMapPropagator()
              .extract(
                  Java8BytecodeBridge.currentContext(),
                  execution,
                  new CamundaActivityExecutionGetter());

      parentScope = parentContext.makeCurrent();

      if (getInstumenter().shouldStart(Java8BytecodeBridge.currentContext(), request)) {
        context = getInstumenter().start(Java8BytecodeBridge.currentContext(), request);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void closeTrace(
        @Advice.Local("request") CamundaCommonRequest request,
        @Advice.Local("otelParentScope") Scope parentScope,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {

      if (context != null && scope != null) {
        getInstumenter().end(context, request, "NA", throwable);
        scope.close();
      }

      if (parentScope != null) {
        parentScope.close();
      }
    }
  }
}
