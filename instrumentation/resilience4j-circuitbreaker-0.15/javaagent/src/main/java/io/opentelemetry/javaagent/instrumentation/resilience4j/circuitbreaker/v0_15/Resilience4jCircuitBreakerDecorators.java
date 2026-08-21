/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
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

  public static <T, R> Function<T, R> wrapFunction(Function<T, R> delegate) {
    return new FunctionWrapper<>(delegate);
  }

  public static <T> Consumer<T> wrapConsumer(Consumer<T> delegate) {
    return new ConsumerWrapper<>(delegate);
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
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.pollPendingSpanAfter(baseline);
        if (pendingSpan != null) {
          result.whenComplete(
              (unused, throwable) -> pendingSpan.end(outcome(throwable), throwable));
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.endAfter(baseline, "failure", t);
        throw t;
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

  private static String outcome(@Nullable Throwable throwable) {
    return throwable == null ? "success" : "failure";
  }

  private Resilience4jCircuitBreakerDecorators() {}
}
