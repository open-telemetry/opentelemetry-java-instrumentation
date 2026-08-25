/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

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

  public static <T> Supplier<T> wrapSupplier(Supplier<T> delegate) {
    return new SupplierWrapper<>(delegate);
  }

  public static <T> Callable<T> wrapCallable(Callable<T> delegate) {
    return new CallableWrapper<>(delegate);
  }

  public static Runnable wrapRunnable(Runnable delegate) {
    return new RunnableWrapper(delegate);
  }

  public static <T> Supplier<CompletionStage<T>> wrapCompletionStageSupplier(
      Supplier<CompletionStage<T>> delegate) {
    return new CompletionStageSupplierWrapper<>(delegate);
  }

  public static <T> Supplier<Future<T>> wrapFutureSupplier(Supplier<Future<T>> delegate) {
    return new FutureSupplierWrapper<>(delegate);
  }

  public static <T, R> Function<T, R> wrapFunction(Function<T, R> delegate) {
    return new FunctionWrapper<>(delegate);
  }

  public static <T> Consumer<T> wrapConsumer(Consumer<T> delegate) {
    return new ConsumerWrapper<>(delegate);
  }

  public static Object wrapChecked(Object delegate) {
    Class<?> delegateClass = delegate.getClass();
    return Proxy.newProxyInstance(
        delegateClass.getClassLoader(),
        delegateClass.getInterfaces(),
        new CheckedInvocationHandler(delegate));
  }

  private static final class SupplierWrapper<T> implements Supplier<T> {

    private final Supplier<T> delegate;

    private SupplierWrapper(Supplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get() {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        T result = delegate.get();
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
      }
    }
  }

  private static final class CallableWrapper<T> implements Callable<T> {

    private final Callable<T> delegate;

    private CallableWrapper(Callable<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T call() throws Exception {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        T result = delegate.call();
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
        return result;
      } catch (Exception e) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", e);
        throw e;
      } catch (Error error) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", error);
        throw error;
      }
    }
  }

  private static final class RunnableWrapper implements Runnable {

    private final Runnable delegate;

    private RunnableWrapper(Runnable delegate) {
      this.delegate = delegate;
    }

    @Override
    public void run() {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        delegate.run();
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
      }
    }
  }

  private static final class CompletionStageSupplierWrapper<T>
      implements Supplier<CompletionStage<T>> {

    private final Supplier<CompletionStage<T>> delegate;

    private CompletionStageSupplierWrapper(Supplier<CompletionStage<T>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public CompletionStage<T> get() {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        CompletionStage<T> result = delegate.get();
        if (baseline != null) {
          Resilience4jCircuitBreakerSpans.detachPendingSpan(baseline);
          try {
            return wrapCompletionStage(result, baseline);
          } catch (Throwable t) {
            baseline.end("failure", t);
            throw t;
          }
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
      }
    }
  }

  private static final class FutureSupplierWrapper<T> implements Supplier<Future<T>> {

    private final Supplier<Future<T>> delegate;

    private FutureSupplierWrapper(Supplier<Future<T>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Future<T> get() {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        Future<T> result = delegate.get();
        if (baseline != null) {
          Resilience4jCircuitBreakerSpans.detachPendingSpan(baseline);
          try {
            return new FutureWrapper<>(result, baseline);
          } catch (Throwable t) {
            baseline.end("failure", t);
            throw t;
          }
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
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
      } finally {
        Resilience4jCircuitBreakerSpans.detachPendingSpan(pendingSpan);
      }
    }
  }

  private static final class FunctionWrapper<T, R> implements Function<T, R> {

    private final Function<T, R> delegate;

    private FunctionWrapper(Function<T, R> delegate) {
      this.delegate = delegate;
    }

    @Override
    public R apply(T value) {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        R result = delegate.apply(value);
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
      }
    }
  }

  private static final class ConsumerWrapper<T> implements Consumer<T> {

    private final Consumer<T> delegate;

    private ConsumerWrapper(Consumer<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(T value) {
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        delegate.accept(value);
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
      }
    }
  }

  private static final class CheckedInvocationHandler implements InvocationHandler {

    private final Object delegate;

    private CheckedInvocationHandler(Object delegate) {
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
      Resilience4jCircuitBreakerSpans.PendingSpan baseline =
          Resilience4jCircuitBreakerSpans.currentPendingSpan();
      try {
        Object result = method.invoke(delegate, args);
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "success", null);
        return result;
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", cause);
        throw cause;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
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
          pendingSpan.end(throwable == null ? "success" : "failure", throwable);
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
