/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.context.ContextKey;
import application.io.opentelemetry.context.ImplicitContextKeyed;
import application.io.opentelemetry.context.Scope;
import com.google.errorprone.annotations.MustBeClosed;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class ContextExtractor implements Context {

  private static final Field otelSpanField;

  static {
    try {
      otelSpanField = OpenTelemetrySpanImpl.class.getDeclaredField("otelSpan");
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
    otelSpanField.setAccessible(true);
  }

  private final Context ref;

  public ContextExtractor(Context appCtx) {
    this.ref = appCtx;
  }

  @Override
  public <V> Context with(ContextKey<V> k1, V v1) {
    if (v1 instanceof OpenTelemetrySpanImpl) {
      Span otelSpan = null;
      try {
        otelSpan = (Span) otelSpanField.get(v1);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
      return ref.with(otelSpan);
    }
    return ref.with(k1, v1);
  }

  @Override
  public Context with(ImplicitContextKeyed value) {
    if (value instanceof OpenTelemetrySpanImpl) {
      Span otelSpan = null;
      try {
        otelSpan = (Span) otelSpanField.get(value);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
      return ref.with(otelSpan);
    }
    return ref.with(value);
  }

  //
  // delegates
  //

  @Nullable
  @Override
  public <V> V get(ContextKey<V> key) {
    return ref.get(key);
  }

  @Override
  @MustBeClosed
  public Scope makeCurrent() {
    return ref.makeCurrent();
  }

  @Override
  public Runnable wrap(Runnable runnable) {
    return ref.wrap(runnable);
  }

  @Override
  public <T> Callable<T> wrap(Callable<T> callable) {
    return ref.wrap(callable);
  }

  @Override
  public Executor wrap(Executor executor) {
    return ref.wrap(executor);
  }

  @Override
  public ExecutorService wrap(ExecutorService executor) {
    return ref.wrap(executor);
  }

  @Override
  public ScheduledExecutorService wrap(ScheduledExecutorService executor) {
    return ref.wrap(executor);
  }

  @Override
  public <T, U> Function<T, U> wrapFunction(Function<T, U> function) {
    return ref.wrapFunction(function);
  }

  @Override
  public <T, U, V> BiFunction<T, U, V> wrapFunction(BiFunction<T, U, V> function) {
    return ref.wrapFunction(function);
  }

  @Override
  public <T> Consumer<T> wrapConsumer(Consumer<T> consumer) {
    return ref.wrapConsumer(consumer);
  }

  @Override
  public <T, U> BiConsumer<T, U> wrapConsumer(BiConsumer<T, U> consumer) {
    return ref.wrapConsumer(consumer);
  }

  @Override
  public <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
    return ref.wrapSupplier(supplier);
  }
}
