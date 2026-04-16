package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import scala.Function0;
import scala.PartialFunction;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

public class TwitterUtilCoreHelpers {
  private TwitterUtilCoreHelpers () {}

  public static class InterruptibleWithContext extends
      AbstractPartialFunction<Throwable, BoxedUnit> {
    private final Context context;
    private final PartialFunction<Throwable, BoxedUnit> delegate;

    public InterruptibleWithContext(Context context,
        PartialFunction<Throwable, BoxedUnit> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public boolean isDefinedAt(Throwable x) {
      Scope scope = context.makeCurrent();
      try {
        // Return true only for inputs this function handles`
        return delegate.isDefinedAt(x);
      } finally {
        scope.close();
      }
    }

    @Override
    public BoxedUnit apply(Throwable x) {
      Scope scope = context.makeCurrent();
      try {
        return delegate.apply(x);
      } finally {
        scope.close();
      }
    }
  }

  public static <T> Function0<T> wrap(Context context, Function0<T> fn) {
    if (context == Context.current()) {
      return fn;
    }
    return () -> {
      Scope scope = context.makeCurrent();
      try {
        System.out.println("func0: " + Span.fromContext(context));
        return fn.apply();
      } finally {
        scope.close();
      }
    };
  }
}
