package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import java.util.concurrent.Callable;

/**
 * Match classes implement {@link Callable}.
 * This is the native image version of {@code io.opentelemetry.javaagent.instrumentation.executors.CallableInstrumentation}.
 */
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
@Aspect(implementInterface = "java.util.concurrent.Callable", onlyWith = Aspect.JDKClassOnly.class)
public class CallableAspect {
  @Advice.Before("call")
  public static Scope beforeCall(@Advice.This Callable<?> task) {
    try {
      VirtualField<Callable<?>, PropagatedContext> virtualField =
          VirtualField.find(Callable.class, PropagatedContext.class);
      return TaskAdviceHelper.makePropagatedContextCurrent(virtualField, task);
    } catch (Throwable t) {
      return null;
    }
  }

  @Advice.After(value = "call", onThrowable = Throwable.class)
  public static void afterCall(@Advice.BeforeResult Scope scope) {
    try {
      if (scope != null) {
        scope.close();
      }
    } catch (Throwable t) {
      //do nothing
    }
  }
}
