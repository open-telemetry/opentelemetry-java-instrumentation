package datadog.trace.instrumentation.rmi;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;

import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class ClientAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(@Advice.Argument(value = 1) final Method method) {
    if (activeSpan() == null) {
      return null;
    }
    final AgentSpan span =
        startSpan("rmi.invoke")
            .setTag(
                DDTags.RESOURCE_NAME,
                method.getDeclaringClass().getSimpleName() + "#" + method.getName())
            .setTag("span.origin.type", method.getDeclaringClass().getCanonicalName());
    return activateSpan(span, true);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    final AgentSpan span = scope.span();
    if (throwable != null) {
      span.setError(true);
      span.addThrowable(throwable);
    }
    scope.close();
  }
}
