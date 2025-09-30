package io.opentelemetry.javaagent.bootstrap.executors;

import io.opentelemetry.context.Context;

public interface WrappedRunnable {

  static boolean shouldDecorateRunnable(Runnable task) {
    // We wrap only lambdas' anonymous classes and if given object has not already been wrapped.
    // Anonymous classes have '/' in class name which is not allowed in 'normal' classes.
    // note: it is always safe to decorate lambdas since downstream code cannot be expecting a
    // specific runnable implementation anyways
    return task.getClass().getName().contains("/") && !(task instanceof WrappedRunnable);
  }

  static Runnable decorate(Runnable task, Context context) {
    return PendingTaskRunnable.measurePendingTime(
        ContextPropagatingRunnable.propagateContext(task, context)
    );
  }

  Runnable getDelegate();

  default Runnable unwrap() {
    Runnable delegate = getDelegate();

    return delegate instanceof WrappedRunnable
        ? ((WrappedRunnable) delegate).unwrap()
        : delegate;
  }

}
