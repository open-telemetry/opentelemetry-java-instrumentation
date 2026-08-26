/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.context.Scope;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class Resilience4jCircuitBreakerDecorators {

  public static <T> Supplier<T> wrapSupplier(CircuitBreaker circuitBreaker, Supplier<T> delegate) {
    return new SupplierWrapper<>(circuitBreaker, delegate);
  }

  public static <T> Callable<T> wrapCallable(CircuitBreaker circuitBreaker, Callable<T> delegate) {
    return new CallableWrapper<>(circuitBreaker, delegate);
  }

  public static Runnable wrapRunnable(CircuitBreaker circuitBreaker, Runnable delegate) {
    return new RunnableWrapper(circuitBreaker, delegate);
  }

  public static <T> Supplier<CompletionStage<T>> wrapCompletionStageSupplier(
      CircuitBreaker circuitBreaker, Supplier<CompletionStage<T>> delegate) {
    return new CompletionStageSupplierWrapper<>(circuitBreaker, delegate);
  }

  public static <T> Supplier<Future<T>> wrapFutureSupplier(
      CircuitBreaker circuitBreaker, Supplier<Future<T>> delegate) {
    return new FutureSupplierWrapper<>(circuitBreaker, delegate);
  }

  public static <T, R> Function<T, R> wrapFunction(
      CircuitBreaker circuitBreaker, Function<T, R> delegate) {
    return new FunctionWrapper<>(circuitBreaker, delegate);
  }

  public static <T> Consumer<T> wrapConsumer(CircuitBreaker circuitBreaker, Consumer<T> delegate) {
    return new ConsumerWrapper<>(circuitBreaker, delegate);
  }

  public static Object wrapChecked(CircuitBreaker circuitBreaker, Object delegate) {
    Class<?> delegateClass = delegate.getClass();
    return Proxy.newProxyInstance(
        delegateClass.getClassLoader(),
        delegateClass.getInterfaces(),
        new CheckedInvocationHandler(circuitBreaker, delegate));
  }

  private static final class SupplierWrapper<T> implements Supplier<T> {

    private final CircuitBreaker circuitBreaker;
    private final Supplier<T> delegate;

    private SupplierWrapper(CircuitBreaker circuitBreaker, Supplier<T> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public T get() {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        T result = delegate.get();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class CallableWrapper<T> implements Callable<T> {

    private final CircuitBreaker circuitBreaker;
    private final Callable<T> delegate;

    private CallableWrapper(CircuitBreaker circuitBreaker, Callable<T> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public T call() throws Exception {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        T result = delegate.call();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
        return result;
      } catch (Exception e) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", e);
        }
        throw e;
      } catch (Error error) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", error);
        }
        throw error;
      }
    }
  }

  private static final class RunnableWrapper implements Runnable {

    private final CircuitBreaker circuitBreaker;
    private final Runnable delegate;

    private RunnableWrapper(CircuitBreaker circuitBreaker, Runnable delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public void run() {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        delegate.run();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class CompletionStageSupplierWrapper<T>
      implements Supplier<CompletionStage<T>> {

    private final CircuitBreaker circuitBreaker;
    private final Supplier<CompletionStage<T>> delegate;

    private CompletionStageSupplierWrapper(
        CircuitBreaker circuitBreaker, Supplier<CompletionStage<T>> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public CompletionStage<T> get() {
      // decorateCompletionStage and decorateFuture have different internal call ordering in
      // Resilience4j. For CompletionStage, instrumentation wraps the user supplier before
      // Resilience4j builds its decorated supplier, so this wrapper runs immediately after
      // permission acquisition. Claim that acquisition before invoking user code, which may perform
      // nested acquisitions.
      Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
          Resilience4jCircuitBreakerSpans.claimRecentAcquisition(circuitBreaker);
      try {
        CompletionStage<T> result;
        try (Scope ignored = pendingSpan == null ? null : pendingSpan.makeCurrent()) {
          result = delegate.get();
        }
        if (pendingSpan == null) {
          return result;
        }
        try {
          return wrapCompletionStage(result, pendingSpan);
        } catch (Throwable t) {
          pendingSpan.end("failure", t);
          throw t;
        }
      } catch (Throwable t) {
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class FutureSupplierWrapper<T> implements Supplier<Future<T>> {

    private final CircuitBreaker circuitBreaker;
    private final Supplier<Future<T>> delegate;

    private FutureSupplierWrapper(CircuitBreaker circuitBreaker, Supplier<Future<T>> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public Future<T> get() {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        Future<T> result = delegate.get();
        // decorateFuture is the opposite of decorateCompletionStage: instrumentation wraps the
        // returned supplier after Resilience4j builds it, so permission is acquired inside
        // delegate.get(). Claim only the span created by that specific acquisition and make the
        // returned Future wrapper own it.
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          try {
            Future<T> wrapped = new FutureWrapper<>(result, pendingSpan);
            pendingSpan.closeOperationScope();
            return wrapped;
          } catch (Throwable t) {
            pendingSpan.end("failure", t);
            throw t;
          }
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class FutureWrapper<T> implements Future<T> {

    private final Future<T> delegate;
    private final Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan;

    private FutureWrapper(
        Future<T> delegate, Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan) {
      this.delegate = delegate;
      this.pendingSpan = pendingSpan;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean result = delegate.cancel(mayInterruptIfRunning);
      if (result) {
        pendingSpan.end("cancelled", null);
      }
      return result;
    }

    @Override
    public boolean isCancelled() {
      return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
      return delegate.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      Resilience4jCircuitBreakerSpans.attachPendingSpan(pendingSpan);
      try {
        T result = delegate.get();
        pendingSpan.end("success", null);
        return result;
      } catch (CancellationException | InterruptedException e) {
        pendingSpan.end("cancelled", null);
        throw e;
      } catch (ExecutionException e) {
        pendingSpan.end("failure", e.getCause() == null ? e : e.getCause());
        throw e;
      } catch (RuntimeException | Error e) {
        pendingSpan.end("failure", e);
        throw e;
      } finally {
        Resilience4jCircuitBreakerSpans.detachPendingSpan(pendingSpan);
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      Resilience4jCircuitBreakerSpans.attachPendingSpan(pendingSpan);
      try {
        T result = delegate.get(timeout, unit);
        pendingSpan.end("success", null);
        return result;
      } catch (CancellationException | InterruptedException e) {
        pendingSpan.end("cancelled", null);
        throw e;
      } catch (ExecutionException e) {
        pendingSpan.end("failure", e.getCause() == null ? e : e.getCause());
        throw e;
      } catch (TimeoutException e) {
        pendingSpan.end("failure", e);
        throw e;
      } catch (RuntimeException | Error e) {
        pendingSpan.end("failure", e);
        throw e;
      } finally {
        Resilience4jCircuitBreakerSpans.detachPendingSpan(pendingSpan);
      }
    }
  }

  private static final class FunctionWrapper<T, R> implements Function<T, R> {

    private final CircuitBreaker circuitBreaker;
    private final Function<T, R> delegate;

    private FunctionWrapper(CircuitBreaker circuitBreaker, Function<T, R> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public R apply(T value) {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        R result = delegate.apply(value);
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class ConsumerWrapper<T> implements Consumer<T> {

    private final CircuitBreaker circuitBreaker;
    private final Consumer<T> delegate;

    private ConsumerWrapper(CircuitBreaker circuitBreaker, Consumer<T> delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public void accept(T value) {
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        delegate.accept(value);
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static final class CheckedInvocationHandler implements InvocationHandler {

    private final CircuitBreaker circuitBreaker;
    private final Object delegate;

    private CheckedInvocationHandler(CircuitBreaker circuitBreaker, Object delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        String methodName = method.getName();
        if ("equals".equals(methodName)) {
          return proxy == args[0];
        } else if ("hashCode".equals(methodName)) {
          return System.identityHashCode(proxy);
        }
        return method.invoke(delegate, args);
      }
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        Object result = method.invoke(delegate, args);
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
        return wrapCheckedAdapterResult(circuitBreaker, method, result);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", cause);
        }
        throw cause;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  private static Object wrapCheckedAdapterResult(
      CircuitBreaker circuitBreaker, Method method, @Nullable Object result) {
    if (result == null) {
      return null;
    }
    if (isCheckedFunctionType(method.getReturnType())) {
      return wrapChecked(circuitBreaker, result);
    }
    if (isFunctionalInterfaceType(method.getReturnType())) {
      return wrapFunctionalAdapter(circuitBreaker, result);
    }
    return result;
  }

  private static boolean isFunctionalInterfaceType(Class<?> type) {
    return type == Supplier.class
        || type == Callable.class
        || type == Runnable.class
        || type == Function.class
        || type == Consumer.class
        || type.getName().startsWith("io.vavr.Function");
  }

  private static Object wrapFunctionalAdapterResult(
      CircuitBreaker circuitBreaker, Method method, @Nullable Object result) {
    if (result == null) {
      return null;
    }
    if (isCheckedFunctionType(method.getReturnType())) {
      return wrapChecked(circuitBreaker, result);
    }
    if (isFunctionalInterfaceType(method.getReturnType())) {
      return wrapFunctionalAdapter(circuitBreaker, result);
    }
    return result;
  }

  private static Object wrapFunctionalAdapter(CircuitBreaker circuitBreaker, Object delegate) {
    Class<?>[] interfaces = delegate.getClass().getInterfaces();
    if (interfaces.length == 0) {
      return delegate;
    }
    return Proxy.newProxyInstance(
        delegate.getClass().getClassLoader(),
        interfaces,
        new FunctionalAdapterInvocationHandler(circuitBreaker, delegate));
  }

  private static boolean isCheckedFunctionType(Class<?> type) {
    return type.isInterface()
        && (type.getName().startsWith("io.github.resilience4j.core.functions.Checked")
            || type.getName().startsWith("io.vavr.CheckedFunction"));
  }

  private static final class FunctionalAdapterInvocationHandler implements InvocationHandler {

    private final CircuitBreaker circuitBreaker;
    private final Object delegate;

    private FunctionalAdapterInvocationHandler(CircuitBreaker circuitBreaker, Object delegate) {
      this.circuitBreaker = circuitBreaker;
      this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        String methodName = method.getName();
        if ("equals".equals(methodName)) {
          return proxy == args[0];
        } else if ("hashCode".equals(methodName)) {
          return System.identityHashCode(proxy);
        }
        return method.invoke(delegate, args);
      }
      Resilience4jCircuitBreakerSpans.Capture capture =
          Resilience4jCircuitBreakerSpans.beginCapture(circuitBreaker);
      try {
        Object result = method.invoke(delegate, args);
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("success", null);
        }
        return wrapFunctionalAdapterResult(circuitBreaker, method, result);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", cause);
        }
        throw cause;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.endCapture(capture);
        if (pendingSpan != null) {
          pendingSpan.end("failure", t);
        }
        throw t;
      }
    }
  }

  @SuppressWarnings("unchecked") // Dynamic proxy implements CompletionStage<T> at runtime.
  private static <T> CompletionStage<T> wrapCompletionStage(
      CompletionStage<T> delegate, Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan) {
    return (CompletionStage<T>)
        Proxy.newProxyInstance(
            delegate.getClass().getClassLoader(),
            new Class<?>[] {CompletionStage.class},
            new CompletionStageInvocationHandler(delegate, pendingSpan));
  }

  private static final class CompletionStageInvocationHandler implements InvocationHandler {

    private final CompletionStage<?> delegate;
    private final Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan;

    private CompletionStageInvocationHandler(
        CompletionStage<?> delegate, Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan) {
      this.delegate = delegate;
      this.pendingSpan = pendingSpan;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        String methodName = method.getName();
        if ("equals".equals(methodName)) {
          return proxy == args[0];
        } else if ("hashCode".equals(methodName)) {
          return System.identityHashCode(proxy);
        }
        return method.invoke(delegate, args);
      }
      Object[] invokedArgs = args;
      if ("whenComplete".equals(method.getName())
          && args != null
          && args.length == 1
          && args[0] instanceof BiConsumer) {
        invokedArgs = new Object[] {wrapWhenComplete((BiConsumer<?, ?>) args[0])};
      }
      try {
        return method.invoke(delegate, invokedArgs);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        pendingSpan.end("failure", cause);
        throw cause;
      } catch (Throwable t) {
        pendingSpan.end("failure", t);
        throw t;
      }
    }

    private BiConsumer<Object, Throwable> wrapWhenComplete(BiConsumer<?, ?> callback) {
      return (result, throwable) -> {
        Resilience4jCircuitBreakerSpans.attachPendingSpan(pendingSpan);
        try {
          invokeWhenComplete(callback, result, throwable);
          pendingSpan.end(
              throwable == null ? "success" : "failure",
              throwable == null
                  ? null
                  : Resilience4jCircuitBreakerSpans.unwrapCompletionException(throwable));
        } catch (Throwable t) {
          pendingSpan.end("failure", t);
          throw t;
        } finally {
          Resilience4jCircuitBreakerSpans.detachPendingSpan(pendingSpan);
        }
      };
    }
  }

  @SuppressWarnings("unchecked") // Callback argument types are erased by CompletionStage.
  private static void invokeWhenComplete(
      BiConsumer<?, ?> callback, @Nullable Object result, @Nullable Throwable throwable) {
    ((BiConsumer<Object, Throwable>) callback).accept(result, throwable);
  }

  private Resilience4jCircuitBreakerDecorators() {}
}
