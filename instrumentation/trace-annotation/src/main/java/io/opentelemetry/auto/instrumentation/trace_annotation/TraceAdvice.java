package io.opentelemetry.auto.instrumentation.trace_annotation;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.trace_annotation.TraceDecorator.DECORATE;

import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.api.Trace;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class TraceAdvice {
  private static final String DEFAULT_OPERATION_NAME = "trace.annotation";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Origin final Method method) {
    final Trace traceAnnotation = method.getAnnotation(Trace.class);
    String operationName = traceAnnotation == null ? null : traceAnnotation.operationName();
    if (operationName == null || operationName.isEmpty()) {
      operationName = DEFAULT_OPERATION_NAME;
    }

    final AgentSpan span = startSpan(operationName);

    String resourceName = traceAnnotation == null ? null : traceAnnotation.resourceName();
    if (resourceName == null || resourceName.isEmpty()) {
      resourceName = DECORATE.spanNameForMethod(method);
    }
    span.setAttribute(MoreTags.RESOURCE_NAME, resourceName);
    DECORATE.afterStart(span);

    return activateSpan(span, true);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
    DECORATE.onError(scope, throwable);
    DECORATE.beforeFinish(scope);
    scope.close();
  }
}
