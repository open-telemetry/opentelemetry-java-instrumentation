/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.spymemcached;

import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

@AutoService(Instrumenter.class)
public final class MemcachedClientInstrumentation extends Instrumenter.Default {

  private static final String MEMCACHED_PACKAGE = "net.spy.memcached";

  public MemcachedClientInstrumentation() {
    super("spymemcached");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(MEMCACHED_PACKAGE + ".MemcachedClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".MemcacheClientTracer",
      packageName + ".CompletionListener",
      packageName + ".SyncCompletionListener",
      packageName + ".GetCompletionListener",
      packageName + ".OperationCompletionListener",
      packageName + ".BulkGetCompletionListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(returns(named(MEMCACHED_PACKAGE + ".internal.OperationFuture")))
            /*
            Flush seems to have a bug when listeners may not be always called.
            Also tracing flush is probably of a very limited value.
            */
            .and(not(named("flush"))),
        MemcachedClientInstrumentation.class.getName() + "$AsyncOperationAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(returns(named(MEMCACHED_PACKAGE + ".internal.GetFuture"))),
        MemcachedClientInstrumentation.class.getName() + "$AsyncGetAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(returns(named(MEMCACHED_PACKAGE + ".internal.BulkFuture"))),
        MemcachedClientInstrumentation.class.getName() + "$AsyncBulkAdvice");
    transformers.put(
        isMethod().and(isPublic()).and(namedOneOf("incr", "decr")),
        MemcachedClientInstrumentation.class.getName() + "$SyncOperationAdvice");
    return transformers;
  }

  public static class AsyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter int callDepth,
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return OperationFuture future) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(MemcachedClient.class);

      if (future != null) {
        OperationCompletionListener listener =
            new OperationCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class AsyncGetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter int callDepth,
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return GetFuture future) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(MemcachedClient.class);

      if (future != null) {
        GetCompletionListener listener =
            new GetCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class AsyncBulkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static int trackCallDepth() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter int callDepth,
        @Advice.This MemcachedClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Return BulkFuture future) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(MemcachedClient.class);

      if (future != null) {
        BulkGetCompletionListener listener =
            new BulkGetCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SyncCompletionListener methodEnter(
        @Advice.This MemcachedClient client, @Advice.Origin("#m") String methodName) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class);
      if (callDepth > 0) {
        return null;
      }

      return new SyncCompletionListener(client.getConnection(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SyncCompletionListener listener, @Advice.Thrown Throwable thrown) {
      if (listener == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(MemcachedClient.class);

      listener.done(thrown);
    }
  }
}
