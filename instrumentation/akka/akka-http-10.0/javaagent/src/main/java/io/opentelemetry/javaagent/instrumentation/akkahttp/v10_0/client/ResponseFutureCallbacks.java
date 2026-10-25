/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.client;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Optional;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import scala.Function1;
import scala.Function2;
import scala.PartialFunction;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.runtime.AbstractFunction2;
import scala.runtime.AbstractPartialFunction;
import scala.util.Properties$;

final class ResponseFutureCallbacks {
  private static final Logger logger = Logger.getLogger(ResponseFutureCallbacks.class.getName());
  private static final VirtualField<Future<?>, Boolean> HTTP_RESPONSE =
      VirtualField.find(Future.class, Boolean.class);
  private static final boolean SCALA_213 =
      Properties$.MODULE$.versionNumberString().startsWith("2.13.");
  private static final ClassValue<Optional<Future<?>>> FAILED_FAILURE_FUTURE =
      new ClassValue<Optional<Future<?>>>() {
        @Override
        protected Optional<Future<?>> computeValue(Class<?> type) {
          try {
            Class<?> futureModuleClass =
                Class.forName("scala.concurrent.Future$", false, type.getClassLoader());
            Object futureModule = futureModuleClass.getField("MODULE$").get(null);
            return Optional.of(
                (Future<?>)
                    futureModuleClass.getMethod("failedFailureFuture").invoke(futureModule));
          } catch (ReflectiveOperationException e) {
            logger.log(FINE, "Unable to resolve Scala failed-future projection", e);
            return Optional.empty();
          }
        }
      };

  static void mark(Future<?> future) {
    // Retain the marker for callbacks registered after completion, without retaining request state.
    HTTP_RESPONSE.set(future, true);
  }

  static void inherit(Future<?> source, Future<?> result) {
    if (HTTP_RESPONSE.get(source) != null) {
      mark(result);
    }
  }

  static void inheritFailed(Future<?> source, Future<?> result) {
    if (HTTP_RESPONSE.get(source) == null) {
      return;
    }
    if (SCALA_213) {
      Optional<Future<?>> cachedFailure = FAILED_FAILURE_FUTURE.get(source.getClass());
      if (!cachedFailure.isPresent() || result == cachedFailure.get()) {
        return;
      }
    }
    mark(result);
  }

  static <T, R> Function1<T, R> wrap(Future<?> future, Function1<T, R> callback) {
    if (HTTP_RESPONSE.get(future) == null
        || callback instanceof ContextFunction1
        || callback instanceof ContextPartialFunction) {
      return callback;
    }
    Context context = Context.current();
    if (callback instanceof PartialFunction) {
      return new ContextPartialFunction<>((PartialFunction<T, R>) callback, context);
    }
    return new ContextFunction1<>(callback, context);
  }

  static <T, U, R> Function2<T, U, R> wrap(Future<?> future, Function2<T, U, R> callback) {
    if (HTTP_RESPONSE.get(future) == null || callback instanceof ContextFunction2) {
      return callback;
    }
    return new ContextFunction2<>(callback, Context.current());
  }

  @Nullable
  private static Scope makeCurrent(Context context) {
    try {
      return context.makeCurrent();
    } catch (Throwable t) {
      logger.log(FINE, "Unable to make HTTP callback context current", t);
      return null;
    }
  }

  private static void close(@Nullable Scope scope) {
    if (scope != null) {
      try {
        scope.close();
      } catch (Throwable t) {
        logger.log(FINE, "Unable to close HTTP callback context scope", t);
      }
    }
  }

  private ResponseFutureCallbacks() {}

  private static final class ContextFunction1<T, R> extends AbstractFunction1<T, R> {
    private final Function1<T, R> delegate;
    private final Context context;

    private ContextFunction1(Function1<T, R> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public R apply(T value) {
      Scope scope = makeCurrent(context);
      try {
        return delegate.apply(value);
      } finally {
        close(scope);
      }
    }
  }

  private static final class ContextFunction2<T, U, R> extends AbstractFunction2<T, U, R> {
    private final Function2<T, U, R> delegate;
    private final Context context;

    private ContextFunction2(Function2<T, U, R> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public R apply(T first, U second) {
      Scope scope = makeCurrent(context);
      try {
        return delegate.apply(first, second);
      } finally {
        close(scope);
      }
    }
  }

  private static final class ContextPartialFunction<T, R> extends AbstractPartialFunction<T, R> {
    private final PartialFunction<T, R> delegate;
    private final Context context;

    private ContextPartialFunction(PartialFunction<T, R> delegate, Context context) {
      this.delegate = delegate;
      this.context = context;
    }

    @Override
    public boolean isDefinedAt(T value) {
      Scope scope = makeCurrent(context);
      try {
        return delegate.isDefinedAt(value);
      } finally {
        close(scope);
      }
    }

    @Override
    public R apply(T value) {
      Scope scope = makeCurrent(context);
      try {
        return delegate.apply(value);
      } finally {
        close(scope);
      }
    }

    @Override
    public <A extends T, B> B applyOrElse(A value, Function1<A, B> fallback) {
      Scope scope = makeCurrent(context);
      try {
        return delegate.applyOrElse(value, fallback);
      } finally {
        close(scope);
      }
    }
  }
}
