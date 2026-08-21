/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import io.vavr.CheckedConsumer;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.CheckedRunnable;
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

  public static <T> CheckedFunction0<T> wrapCheckedFunction0(CheckedFunction0<T> delegate) {
    return new CheckedFunction0Wrapper<>(delegate);
  }

  public static CheckedRunnable wrapCheckedRunnable(CheckedRunnable delegate) {
    return new CheckedRunnableWrapper(delegate);
  }

  public static <T, R> CheckedFunction1<T, R> wrapCheckedFunction1(
      CheckedFunction1<T, R> delegate) {
    return new CheckedFunction1Wrapper<>(delegate);
  }

  public static <T> CheckedConsumer<T> wrapCheckedConsumer(CheckedConsumer<T> delegate) {
    return new CheckedConsumerWrapper<>(delegate);
  }

  private static final class SupplierWrapper<T> implements Supplier<T> {

    private final Supplier<T> delegate;

    private SupplierWrapper(Supplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get() {
      try {
        T result = delegate.get();
        Resilience4jCircuitBreakerSpans.end("success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
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
      try {
        T result = delegate.call();
        Resilience4jCircuitBreakerSpans.end("success", null);
        return result;
      } catch (Exception e) {
        Resilience4jCircuitBreakerSpans.end("failure", e);
        throw e;
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
      try {
        delegate.run();
        Resilience4jCircuitBreakerSpans.end("success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
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
      try {
        CompletionStage<T> result = delegate.get();
        Resilience4jCircuitBreakerSpans.PendingSpan pendingSpan =
            Resilience4jCircuitBreakerSpans.pollPendingSpan();
        if (pendingSpan != null) {
          result.whenComplete(
              (unused, throwable) -> pendingSpan.end(outcome(throwable), throwable));
        }
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
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
      try {
        R result = delegate.apply(value);
        Resilience4jCircuitBreakerSpans.end("success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
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
      try {
        delegate.accept(value);
        Resilience4jCircuitBreakerSpans.end("success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
        throw t;
      }
    }
  }

  private static final class CheckedFunction0Wrapper<T> implements CheckedFunction0<T> {

    private final CheckedFunction0<T> delegate;

    private CheckedFunction0Wrapper(CheckedFunction0<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T apply() throws Throwable {
      try {
        T result = delegate.apply();
        Resilience4jCircuitBreakerSpans.end("success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
        throw t;
      }
    }
  }

  private static final class CheckedRunnableWrapper implements CheckedRunnable {

    private final CheckedRunnable delegate;

    private CheckedRunnableWrapper(CheckedRunnable delegate) {
      this.delegate = delegate;
    }

    @Override
    public void run() throws Throwable {
      try {
        delegate.run();
        Resilience4jCircuitBreakerSpans.end("success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
        throw t;
      }
    }
  }

  private static final class CheckedFunction1Wrapper<T, R> implements CheckedFunction1<T, R> {

    private final CheckedFunction1<T, R> delegate;

    private CheckedFunction1Wrapper(CheckedFunction1<T, R> delegate) {
      this.delegate = delegate;
    }

    @Override
    public R apply(T value) throws Throwable {
      try {
        R result = delegate.apply(value);
        Resilience4jCircuitBreakerSpans.end("success", null);
        return result;
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
        throw t;
      }
    }
  }

  private static final class CheckedConsumerWrapper<T> implements CheckedConsumer<T> {

    private final CheckedConsumer<T> delegate;

    private CheckedConsumerWrapper(CheckedConsumer<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void accept(T value) throws Throwable {
      try {
        delegate.accept(value);
        Resilience4jCircuitBreakerSpans.end("success", null);
      } catch (Throwable t) {
        Resilience4jCircuitBreakerSpans.end("failure", t);
        throw t;
      }
    }
  }

  private static String outcome(@Nullable Throwable throwable) {
    return throwable == null ? "success" : "failure";
  }

  private Resilience4jCircuitBreakerDecorators() {}
}
