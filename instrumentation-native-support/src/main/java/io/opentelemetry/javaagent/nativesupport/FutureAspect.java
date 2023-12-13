package io.opentelemetry.javaagent.nativesupport;

import com.oracle.svm.core.annotate.Advice;
import com.oracle.svm.core.annotate.Aspect;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * This is the native image version of
 * {@code io.opentelemetry.javaagent.instrumentation.executors.FutureInstrumentation}.
 */
@SuppressWarnings({"OtelPrivateConstructorForUtilityClass", "EmptyCatch"})
@Aspect(matchers = {
    "java.util.concurrent.CompletableFuture$BiRelay",
    "java.util.concurrent.CompletableFuture$ThreadPerTaskExecutor",
    "java.util.concurrent.ForkJoinTask",
    "java.util.concurrent.ForkJoinTask$AdaptedCallable",
    "java.util.concurrent.FutureTask",
    "java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask",
})
public class FutureAspect {

  @Advice.After(onlyWithReturnType = IsBoolean.class, notFoundAction = Advice.NotFoundAction.info)
  public static void cancel(boolean mayInterruptIfRunning, @Advice.This Future<?> future) {
    try {
      VirtualField<Future<?>, PropagatedContext> virtualField = VirtualField.find(Future.class,
          PropagatedContext.class);
      ExecutorAdviceHelper.cleanPropagatedContext(virtualField, future);
    } catch (Throwable t) {

    }

  }

  static class IsBoolean implements Predicate<Class<?>> {

    @Override
    public boolean test(Class<?> clazz) {
      return clazz.equals(boolean.class);
    }
  }
}
