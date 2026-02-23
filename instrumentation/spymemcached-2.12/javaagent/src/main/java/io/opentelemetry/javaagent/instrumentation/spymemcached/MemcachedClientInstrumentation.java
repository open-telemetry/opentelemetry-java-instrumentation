/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

public class MemcachedClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("net.spy.memcached.MemcachedClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(returns(named("net.spy.memcached.internal.OperationFuture")))
            // Flush seems to have a bug when listeners may not be always called.
            // Also tracing flush is probably of a very limited value.
            .and(not(named("flush"))),
        this.getClass().getName() + "$AsyncOperationAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("net.spy.memcached.internal.GetFuture"))),
        this.getClass().getName() + "$AsyncGetAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("net.spy.memcached.internal.BulkFuture"))),
        this.getClass().getName() + "$AsyncBulkAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(namedOneOf("incr", "decr")),
        this.getClass().getName() + "$SyncOperationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AsyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope<OperationFuture<?>, OperationCompletionListener> methodEnter(
        @Advice.This MemcachedClient client, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(AsyncOperationHandler.INSTANCE, client, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return @Nullable OperationFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<OperationFuture<?>, OperationCompletionListener> adviceScope) {
      adviceScope.end(future, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncGetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope<GetFuture<?>, GetCompletionListener> methodEnter(
        @Advice.This MemcachedClient client, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(AsyncGetHandler.INSTANCE, client, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return @Nullable GetFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<GetFuture<?>, GetCompletionListener> adviceScope) {
      adviceScope.end(future, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncBulkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope<BulkFuture<?>, BulkGetCompletionListener> methodEnter(
        @Advice.This MemcachedClient client, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(AsyncBulkHandler.INSTANCE, client, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return @Nullable BulkFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<BulkFuture<?>, BulkGetCompletionListener> adviceScope) {
      adviceScope.end(future, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope<Void, SyncCompletionListener> methodEnter(
        @Advice.This MemcachedClient client, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(SyncHandler.INSTANCE, client, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<Void, SyncCompletionListener> adviceScope) {
      adviceScope.end(null, thrown);
    }
  }

  public static class AdviceScope<F, T extends CompletionListener<?>> {
    private final CallDepth callDepth;
    private final Handler<F, T> handler;
    @Nullable private final T listener;
    @Nullable private final Scope scope;

    private AdviceScope(Handler<F, T> handler, CallDepth callDepth, @Nullable T listener) {
      this.handler = handler;
      this.callDepth = callDepth;
      this.listener = listener;
      this.scope = listener != null ? listener.getContext().makeCurrent() : null;
    }

    public static <F, T extends CompletionListener<?>> AdviceScope<F, T> start(
        Handler<F, T> handler, MemcachedClient client, String methodName) {
      CallDepth callDepth = CallDepth.forClass(MemcachedClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope<>(handler, callDepth, null);
      }

      return new AdviceScope<>(
          handler,
          callDepth,
          handler.create(Context.current(), client.getConnection(), methodName));
    }

    public void end(@Nullable F future, @Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0 || listener == null || scope == null) {
        return;
      }
      scope.close();

      // when throwable is set then future is always null as it is the return value of the
      // instrumented method
      if (future == null) {
        listener.done(throwable);
      } else {
        handler.addListener(future, listener);
      }
    }
  }

  public interface Handler<F, T extends CompletionListener<?>> {
    T create(Context parentContext, MemcachedConnection connection, String methodName);

    default void addListener(F future, T listener) {}
  }

  public enum AsyncOperationHandler
      implements Handler<OperationFuture<?>, OperationCompletionListener> {
    INSTANCE;

    @Override
    public OperationCompletionListener create(
        Context parentContext, MemcachedConnection connection, String methodName) {
      return OperationCompletionListener.create(parentContext, connection, methodName);
    }

    @Override
    public void addListener(OperationFuture<?> future, OperationCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum AsyncGetHandler implements Handler<GetFuture<?>, GetCompletionListener> {
    INSTANCE;

    @Override
    public GetCompletionListener create(
        Context parentContext, MemcachedConnection connection, String methodName) {
      return GetCompletionListener.create(parentContext, connection, methodName);
    }

    @Override
    public void addListener(GetFuture<?> future, GetCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum AsyncBulkHandler implements Handler<BulkFuture<?>, BulkGetCompletionListener> {
    INSTANCE;

    @Override
    public BulkGetCompletionListener create(
        Context parentContext, MemcachedConnection connection, String methodName) {
      return BulkGetCompletionListener.create(parentContext, connection, methodName);
    }

    @Override
    public void addListener(BulkFuture<?> future, BulkGetCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum SyncHandler implements Handler<Void, SyncCompletionListener> {
    INSTANCE;

    @Override
    public SyncCompletionListener create(
        Context parentContext, MemcachedConnection connection, String methodName) {
      return SyncCompletionListener.create(parentContext, connection, methodName);
    }
  }
}
