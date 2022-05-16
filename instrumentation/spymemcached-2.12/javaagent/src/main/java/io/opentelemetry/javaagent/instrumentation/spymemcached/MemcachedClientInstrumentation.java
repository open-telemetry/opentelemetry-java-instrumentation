/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedClient;
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
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(MemcachedClient.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return OperationFuture<?> future,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (future != null) {
        OperationCompletionListener listener =
            OperationCompletionListener.create(
                currentContext(), client.getConnection(), methodName);
        if (listener != null) {
          future.addListener(listener);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncGetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(MemcachedClient.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return GetFuture<?> future,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (future != null) {
        GetCompletionListener listener =
            GetCompletionListener.create(currentContext(), client.getConnection(), methodName);
        if (listener != null) {
          future.addListener(listener);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncBulkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(MemcachedClient.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return BulkFuture<?> future,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (future != null) {
        BulkGetCompletionListener listener =
            BulkGetCompletionListener.create(currentContext(), client.getConnection(), methodName);
        if (listener != null) {
          future.addListener(listener);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SyncCompletionListener methodEnter(
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(MemcachedClient.class);
      if (callDepth.getAndIncrement() > 0) {
        return null;
      }

      return SyncCompletionListener.create(currentContext(), client.getConnection(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SyncCompletionListener listener,
        @Advice.Thrown Throwable thrown,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0 || listener == null) {
        return;
      }

      listener.done(thrown);
    }
  }
}
