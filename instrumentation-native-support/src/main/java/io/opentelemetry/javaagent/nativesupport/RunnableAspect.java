package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;

/**
 * This is the native image version of
 * {@code io.opentelemetry.javaagent.instrumentation.executors.RunnableInstrumentation}.
 */
@SuppressWarnings({"OtelPrivateConstructorForUtilityClass", "EmptyCatch"})
@Aspect(implementInterface = "java.lang.Runnable", onlyWith = Aspect.JDKClassOnly.class)
public class RunnableAspect {

  @Advice.Before("run")
  public static Scope beforeRun(@Advice.This Runnable thiz) {
    try {
      VirtualField<Runnable, PropagatedContext> virtualField = VirtualField.find(Runnable.class,
          PropagatedContext.class);
      return TaskAdviceHelper.makePropagatedContextCurrent(virtualField, thiz);
    } catch (Throwable t) {
      return null;
    }

  }

  @Advice.After(value = "run", onThrowable = Throwable.class)
  public static void afterRun(@Advice.BeforeResult Scope scope) {
    try {
      if (scope != null) {
        scope.close();
      }
    } catch (Throwable t) {

    }

  }
}
