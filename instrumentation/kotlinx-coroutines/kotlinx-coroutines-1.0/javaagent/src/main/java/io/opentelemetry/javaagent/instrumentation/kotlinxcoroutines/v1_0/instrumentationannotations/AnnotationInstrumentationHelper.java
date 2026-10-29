/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations;

import static io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations.AnnotationSingletons.instrumenter;
import static java.util.logging.Level.FINE;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.extension.kotlin.ContextExtensionsKt;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.ContinuationInterceptor;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
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

  @SuppressWarnings("unchecked") // the frame passes the callee's result through unchanged
  public static <T> Continuation<T> wrapContinuation(
      Continuation<T> continuation, Context context, Context parentContext, Object request) {
    return (Continuation<T>)
        new SuspendFrame(
            new ContextContinuation<>(continuation, context, parentContext, request),
            continuation.getContext());
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
      if (continuation != null && continuation instanceof SuspendFrame) {
        // the callee completed without suspending, so the frame never completes and never
        // releases the continuation it intercepted when it was created
        ((SuspendFrame) continuation).release();
      }
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
    private final Context parentContext;
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
      this.parentContext = parentContext;
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

      // Resuming the caller here switches the thread context in the middle of a task the runtime
      // started with another context. Under an undispatched withContext the runtime may restore
      // that task's context later, out of order with the switch, so let the caller's interceptor
      // resume it with its own context instead.
      if (Context.current() != parentContext && UndispatchedMarker.mayBeIn(delegate.getContext())) {
        Continuation<T> intercepted = IntrinsicsKt.intercepted(delegate);
        if (intercepted != delegate) {
          intercepted.resumeWith(result);
          return;
        }
      }

      Scope scope = delegateContextElement.updateThreadContext(delegate.getContext());
      try {
        delegate.resumeWith(result);
      } finally {
        delegateContextElement.restoreThreadContext(delegate.getContext(), scope);
      }
    }
  }

  /**
   * Stands in for the frame the compiler omits when the annotated method tail calls another suspend
   * function. Coroutine runtimes intercept only {@link ContinuationImpl} frames, so without it a
   * callee completing on another dispatcher would resume the caller on the callee's thread.
   */
  public static final class SuspendFrame extends ContinuationImpl {

    private static final Logger logger = Logger.getLogger(SuspendFrame.class.getName());

    // the cache behind ContinuationImpl.intercepted()
    @Nullable private static final Field INTERCEPTED = findInterceptedField();

    @SuppressWarnings("unchecked") // ContinuationImpl takes Continuation<Object>
    SuspendFrame(Continuation<?> completion, CoroutineContext callerContext) {
      super((Continuation<Object>) completion);
      // Intercept a stand-in that carries the caller's context instead of this frame, whose
      // context also holds the span: the runtime then applies the caller's thread context when it
      // resumes the caller, as it would without the annotation.
      ContinuationInterceptor interceptor = callerContext.get(ContinuationInterceptor.Key);
      if (interceptor != null && INTERCEPTED != null) {
        try {
          INTERCEPTED.set(
              this, interceptor.interceptContinuation(new CallerResumption(this, callerContext)));
        } catch (IllegalAccessException | RuntimeException e) {
          // fall back to intercepting this frame
          logger.log(FINE, "Failed to set the intercepted continuation of the tail call frame", e);
        }
      }
    }

    @Nullable
    private static Field findInterceptedField() {
      try {
        Field field = ContinuationImpl.class.getDeclaredField("intercepted");
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException | RuntimeException e) {
        return null;
      }
    }

    void release() {
      releaseIntercepted();
    }

    @Override
    @SuppressWarnings("OtelCanIgnoreReturnValueSuggester") // returns the callee's result
    protected Object invokeSuspend(@NotNull Object result) {
      ResultKt.throwOnFailure(result);
      return result;
    }
  }

  private static final class UndispatchedMarker {
    // kotlinx-coroutines marks the context of every undispatched withContext with this element
    @Nullable private static final CoroutineContext.Key<?> KEY = findKey();

    static boolean mayBeIn(CoroutineContext context) {
      return KEY == null || context.get(KEY) != null;
    }

    @Nullable
    private static CoroutineContext.Key<?> findKey() {
      try {
        Class<?> marker =
            Class.forName(
                "kotlinx.coroutines.UndispatchedMarker",
                false,
                ThreadContextElement.class.getClassLoader());
        // the class is not public
        Field field = marker.getField("INSTANCE");
        field.setAccessible(true);
        Object instance = field.get(null);
        return instance instanceof CoroutineContext.Key ? (CoroutineContext.Key<?>) instance : null;
      } catch (ReflectiveOperationException | RuntimeException e) {
        return null;
      }
    }

    private UndispatchedMarker() {}
  }

  private static final class CallerResumption implements Continuation<Object>, CoroutineStackFrame {
    private final SuspendFrame frame;
    private final CoroutineContext callerContext;

    CallerResumption(SuspendFrame frame, CoroutineContext callerContext) {
      this.frame = frame;
      this.callerContext = callerContext;
    }

    @NotNull
    @Override
    public CoroutineContext getContext() {
      return callerContext;
    }

    @Override
    public void resumeWith(@NotNull Object result) {
      frame.resumeWith(result);
    }

    @Override
    public CoroutineStackFrame getCallerFrame() {
      return frame;
    }

    @Nullable
    @Override
    public StackTraceElement getStackTraceElement() {
      return null;
    }
  }

  public static void init() {}

  private AnnotationInstrumentationHelper() {}
}
