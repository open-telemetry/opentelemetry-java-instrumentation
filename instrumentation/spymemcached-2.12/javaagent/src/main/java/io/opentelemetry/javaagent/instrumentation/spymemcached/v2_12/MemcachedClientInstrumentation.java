/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

class MemcachedClientInstrumentation implements TypeInstrumentation {
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
        getClass().getName() + "$AsyncOperationAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("net.spy.memcached.internal.GetFuture"))),
        getClass().getName() + "$AsyncGetAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(returns(named("net.spy.memcached.internal.BulkFuture"))),
        getClass().getName() + "$AsyncBulkAdvice");
    transformer.applyAdviceToMethod(
        isPublic().and(namedOneOf("incr", "decr")), getClass().getName() + "$SyncOperationAdvice");
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("getBulk"))
            .and(takesArgument(0, Collection.class))
            .and(takesArgument(1, named("net.spy.memcached.transcoders.Transcoder"))),
        getClass().getName() + "$SyncBulkAdvice");
  }

  @SuppressWarnings("unused")
  public static class AsyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope<OperationFuture<?>, OperationCompletionListener> methodEnter(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#d") String descriptor,
        @Advice.AllArguments Object[] args) {
      return AdviceScope.start(
          AsyncOperationHandler.INSTANCE, client, methodName, descriptor, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Return @Nullable OperationFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<OperationFuture<?>, OperationCompletionListener> adviceScope) {
      adviceScope.end(future, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncGetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope<GetFuture<?>, GetCompletionListener> methodEnter(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#d") String descriptor,
        @Advice.AllArguments Object[] args) {
      return AdviceScope.start(AsyncGetHandler.INSTANCE, client, methodName, descriptor, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Return @Nullable GetFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<GetFuture<?>, GetCompletionListener> adviceScope) {
      adviceScope.end(future, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncBulkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope<BulkFuture<?>, BulkGetCompletionListener> methodEnter(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#d") String descriptor,
        @Advice.AllArguments Object[] args) {
      return AdviceScope.start(AsyncBulkHandler.INSTANCE, client, methodName, descriptor, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Return @Nullable BulkFuture<?> future,
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter @Nullable AdviceScope<BulkFuture<?>, BulkGetCompletionListener> adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(future, thrown);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope<Void, SyncCompletionListener> methodEnter(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#d") String descriptor,
        @Advice.AllArguments Object[] args) {
      return AdviceScope.start(SyncHandler.INSTANCE, client, methodName, descriptor, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter AdviceScope<Void, SyncCompletionListener> adviceScope) {
      adviceScope.end(null, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class SyncBulkAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Scope methodEnter(@Advice.Argument(0) Collection<String> keys) {
      String queryText = SpymemcachedQuerySanitizer.sanitizeBulk(keys);
      if (queryText.equals("getBulk")) {
        return null;
      }
      return Context.current()
          .with(SpymemcachedRequest.BULK_QUERY_TEXT, new AtomicReference<>(queryText))
          .makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
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
        Handler<F, T> handler,
        MemcachedClient client,
        String methodName,
        String descriptor,
        Object[] args) {
      CallDepth callDepth = CallDepth.forClass(MemcachedClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope<>(handler, callDepth, null);
      }

      try {
        SpymemcachedRequest request =
            SpymemcachedRequest.create(client.getConnection(), methodName, descriptor, args);
        // Consume the synchronous bulk snapshot without propagating it to nested client calls.
        Context parentContext = Context.current().with(SpymemcachedRequest.BULK_QUERY_TEXT, null);
        return new AdviceScope<>(handler, callDepth, handler.create(parentContext, request));
      } catch (Throwable t) {
        callDepth.decrementAndGet();
        throw t;
      }
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
    @Nullable
    T create(Context parentContext, SpymemcachedRequest request);

    default void addListener(F future, T listener) {}
  }

  public enum AsyncOperationHandler
      implements Handler<OperationFuture<?>, OperationCompletionListener> {
    INSTANCE;

    @Override
    @Nullable
    public OperationCompletionListener create(Context parentContext, SpymemcachedRequest request) {
      return OperationCompletionListener.create(parentContext, request);
    }

    @Override
    public void addListener(OperationFuture<?> future, OperationCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum AsyncGetHandler implements Handler<GetFuture<?>, GetCompletionListener> {
    INSTANCE;

    @Override
    @Nullable
    public GetCompletionListener create(Context parentContext, SpymemcachedRequest request) {
      return GetCompletionListener.create(parentContext, request);
    }

    @Override
    public void addListener(GetFuture<?> future, GetCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum AsyncBulkHandler implements Handler<BulkFuture<?>, BulkGetCompletionListener> {
    INSTANCE;

    @Override
    @Nullable
    public BulkGetCompletionListener create(Context parentContext, SpymemcachedRequest request) {
      return BulkGetCompletionListener.create(parentContext, request);
    }

    @Override
    public void addListener(BulkFuture<?> future, BulkGetCompletionListener listener) {
      future.addListener(listener);
    }
  }

  public enum SyncHandler implements Handler<Void, SyncCompletionListener> {
    INSTANCE;

    @Override
    @Nullable
    public SyncCompletionListener create(Context parentContext, SpymemcachedRequest request) {
      return SyncCompletionListener.create(parentContext, request);
    }
  }
}
