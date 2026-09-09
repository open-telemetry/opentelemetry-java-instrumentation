/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations;

import static io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations.AnnotationSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.kotlin.ContextExtensionsKt;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.CoroutineStackFrame;
import kotlinx.coroutines.ThreadContextElement;
import org.jetbrains.annotations.NotNull;

/**
 * Instrumentation helper that is called through bytecode instrumentation. When using invokedynamic
 * instrumentation this class is called through an injected proxy, and thus it should not pull any
 * other class references than the ones that are already present in the target classloader or the
 * bootstrap classloader. This is why the {@link MethodRequest} class is here passed as an {@link
 * Object} as it allows to avoid having to inject extra classes in the target classloader
 */
@SuppressWarnings("unused") // methods calls injected through bytecode instrumentation
public class AnnotationInstrumentationHelper {

  private static final VirtualField<Continuation<?>, Context> CONTEXT_FIELD =
      VirtualField.find(Continuation.class, Context.class);

  public static Object createMethodRequest(
      Class<?> declaringClass,
      String methodName,
      @Nullable String withSpanValue,
      @Nullable String spanKindString) {
    SpanKind spanKind = SpanKind.INTERNAL;
    if (spanKindString != null) {
      try {
        spanKind = SpanKind.valueOf(spanKindString);
      } catch (IllegalArgumentException ignored) {
        // ignore
      }
    }

    return MethodRequest.create(declaringClass, methodName, withSpanValue, spanKind);
  }

  @Nullable
  public static Context enterCoroutine(
      int label, @Nullable Continuation<?> continuation, Object request) {
    // label 0 means that coroutine is started, any other label means that coroutine is resumed
    if (label == 0) {
      Context context = instrumenter().start(Context.current(), (MethodRequest) request);
      // null continuation means that this method is not going to be resumed, and we don't need to
      // store the context
      if (continuation != null) {
        CONTEXT_FIELD.set(continuation, context);
      }
      return context;
    } else {
      return continuation != null ? CONTEXT_FIELD.get(continuation) : null;
    }
  }

  public static Context currentContext() {
    return Context.current();
  }

  public static <T> Continuation<T> wrapContinuation(
      Continuation<T> continuation, Context context, Context parentContext, Object request) {
    return new ContextContinuation<>(continuation, context, parentContext, request);
  }

  @Nullable
  public static Scope openScope(@Nullable Context context) {
    return context != null ? context.makeCurrent() : null;
  }

  public static void exitCoroutine(
      @Nullable Object result,
      @Nullable Object request,
      @Nullable Continuation<?> continuation,
      @Nullable Context context,
      @Nullable Scope scope) {
    exitCoroutine(null, result, request, continuation, context, scope);
  }

  public static void exitCoroutine(
      @Nullable Throwable error,
      @Nullable Object result,
      @Nullable Object request,
      @Nullable Continuation<?> continuation,
      @Nullable Context context,
      @Nullable Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    // end the span when this method can not be resumed (coroutine is null) or if it has reached
    // final state (returns anything else besides COROUTINE_SUSPENDED)
    if (continuation == null || result != IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
      instrumenter().end(context, (MethodRequest) request, null, error);
    }
  }

  public static void setSpanAttribute(int label, String name, boolean value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, byte value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, char value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, String.valueOf(value));
    }
  }

  public static void setSpanAttribute(int label, String name, double value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, float value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, int value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, long value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, short value) {
    // only add the attribute when coroutine is started
    if (label == 0) {
      Span.current().setAttribute(name, value);
    }
  }

  public static void setSpanAttribute(int label, String name, Object value) {
    // only add the attribute when coroutine is started
    if (label != 0) {
      return;
    }
    if (value instanceof String) {
      Span.current().setAttribute(name, (String) value);
    } else if (value instanceof Boolean) {
      Span.current().setAttribute(name, (Boolean) value);
    } else if (value instanceof Byte) {
      Span.current().setAttribute(name, (Byte) value);
    } else if (value instanceof Character) {
      Span.current().setAttribute(name, value.toString());
    } else if (value instanceof Double) {
      Span.current().setAttribute(name, (Double) value);
    } else if (value instanceof Float) {
      Span.current().setAttribute(name, (Float) value);
    } else if (value instanceof Integer) {
      Span.current().setAttribute(name, (Integer) value);
    } else if (value instanceof Long) {
      Span.current().setAttribute(name, (Long) value);
    }
    // TODO: arrays and List not supported see AttributeBindingFactoryTest
  }

  public static final class ContextContinuation<T> implements Continuation<T>, CoroutineStackFrame {
    private final Continuation<T> delegate;
    private final CoroutineContext coroutineContext;
    private final ThreadContextElement<Scope> delegateContextElement;
    private final Context spanContext;
    private final Object request;

    @SuppressWarnings("unchecked") // asContextElement returns CoroutineContext to Java
    ContextContinuation(
        Continuation<T> delegate, Context spanContext, Context parentContext, Object request) {
      this.delegate = delegate;
      this.coroutineContext =
          delegate.getContext().plus(ContextExtensionsKt.asContextElement(spanContext));
      this.delegateContextElement =
          (ThreadContextElement<Scope>) ContextExtensionsKt.asContextElement(parentContext);
      this.spanContext = spanContext;
      this.request = request;
    }

    @NotNull
    @Override
    public CoroutineContext getContext() {
      return coroutineContext;
    }

    @Nullable
    @Override
    public CoroutineStackFrame getCallerFrame() {
      return delegate instanceof CoroutineStackFrame ? (CoroutineStackFrame) delegate : null;
    }

    @Nullable
    @Override
    public StackTraceElement getStackTraceElement() {
      return null;
    }

    @Override
    public void resumeWith(@NotNull Object result) {
      Throwable error = KotlinResultUtilKt.exceptionOrNull(result);
      instrumenter().end(spanContext, (MethodRequest) request, null, error);

      Scope scope = delegateContextElement.updateThreadContext(delegate.getContext());
      try {
        delegate.resumeWith(result);
      } finally {
        delegateContextElement.restoreThreadContext(delegate.getContext(), scope);
      }
    }
  }

  public static void init() {}

  private AnnotationInstrumentationHelper() {}
}
