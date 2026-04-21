/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import com.twitter.util.Promise;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import scala.Function0;
import scala.Function1;
import scala.PartialFunction;
import scala.runtime.AbstractPartialFunction;
import scala.runtime.BoxedUnit;

public class TwitterUtilCoreHelpers {
  public static final VirtualField<Promise.K, Context> PROMISE_K_CONTEXT_FIELD =
      VirtualField.find(Promise.K.class, Context.class);

  private TwitterUtilCoreHelpers() {}

  public static class InterruptibleWithContext
      extends AbstractPartialFunction<Throwable, BoxedUnit> {
    private final Context context;
    private final PartialFunction<Throwable, BoxedUnit> delegate;

    public InterruptibleWithContext(
        Context context, PartialFunction<Throwable, BoxedUnit> delegate) {
      this.context = context;
      this.delegate = delegate;
    }

    @Override
    public boolean isDefinedAt(Throwable x) {
      try (Scope ignored = context.makeCurrent()) {
        // Return true only for inputs this function handles`
        return delegate.isDefinedAt(x);
      }
    }

    @Override
    public BoxedUnit apply(Throwable x) {
      try (Scope ignored = context.makeCurrent()) {
        return delegate.apply(x);
      }
    }
  }

  public static <T, O> Function1<T, O> wrap(Context context, Function1<T, O> fn) {
    return (t) -> {
      // always set it: you never know what might be polluting the thread local context at the time
      try (Scope ignored = context.makeCurrent()) {
        return fn.apply(t);
      }
    };
  }

  public static <T> Function0<T> wrap(Context context, Function0<T> fn) {
    return () -> {
      // always set it: you never know what might be polluting the thread local context at the time
      try (Scope ignored = context.makeCurrent()) {
        return fn.apply();
      }
    };
  }
}
