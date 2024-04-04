/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

final class SpanSuppressors {

  private SpanSuppressors() {}

  enum Noop implements SpanSuppressor {
    INSTANCE;

    @Override
    @CanIgnoreReturnValue
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      return context;
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      return false;
    }
  }

  static final class DelegateBySpanKind implements SpanSuppressor {

    private final Map<SpanKind, SpanSuppressor> delegates;

    DelegateBySpanKind(Map<SpanKind, SpanSuppressor> delegates) {
      this.delegates = delegates;
    }

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      SpanSuppressor delegate = delegates.get(spanKind);
      if (delegate == null) {
        return context;
      }
      return delegate.storeInContext(context, spanKind, span);
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      SpanSuppressor delegate = delegates.get(spanKind);
      if (delegate == null) {
        return false;
      }
      return delegate.shouldSuppress(parentContext, spanKind);
    }
  }

  static final class BySpanKey implements SpanSuppressor {

    private final SpanKey[] spanKeys;

    BySpanKey(Set<SpanKey> spanKeys) {
      this.spanKeys = spanKeys.toArray(new SpanKey[0]);
    }

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      for (SpanKey spanKey : spanKeys) {
        context = spanKey.storeInContext(context, span);
      }
      return context;
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      for (SpanKey spanKey : spanKeys) {
        if (spanKey.fromContextOrNull(parentContext) == null) {
          return false;
        }
      }
      return true;
    }
  }

  static class ByContextKey implements SpanSuppressor {
    private final SpanSuppressor delegate;
    private final Method shouldSuppressInstrumentation;

    ByContextKey(SpanSuppressor delegate) {
      this.delegate = delegate;
      Method shouldSuppressInstrumentation;
      try {
        Class<?> instrumentationUtil =
            Class.forName("io.opentelemetry.exporter.internal.InstrumentationUtil");
        shouldSuppressInstrumentation =
            instrumentationUtil.getDeclaredMethod("shouldSuppressInstrumentation", Context.class);
      } catch (ClassNotFoundException | NoSuchMethodException e) {
        shouldSuppressInstrumentation = null;
      }
      this.shouldSuppressInstrumentation = shouldSuppressInstrumentation;
    }

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      return delegate.storeInContext(context, spanKind, span);
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      if (suppressByContextKey(parentContext)) {
        return true;
      }
      return delegate.shouldSuppress(parentContext, spanKind);
    }

    private boolean suppressByContextKey(Context context) {
      if (shouldSuppressInstrumentation == null) {
        return false;
      }

      try {
        return (boolean) shouldSuppressInstrumentation.invoke(null, context);
      } catch (IllegalAccessException | InvocationTargetException e) {
        return false;
      }
    }
  }
}
