/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.spymemcached;

import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.api.CallDepthThreadLocalMap;
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
      packageName + ".MemcacheClientDecorator",
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
        @Advice.Enter final int callDepth,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final OperationFuture future) {
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
        @Advice.Enter final int callDepth,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final GetFuture future) {
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
        @Advice.Enter final int callDepth,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final BulkFuture future) {
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
        @Advice.This final MemcachedClient client, @Advice.Origin("#m") final String methodName) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class);
      if (callDepth > 0) {
        return null;
      }

      return new SyncCompletionListener(client.getConnection(), methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SyncCompletionListener listener,
        @Advice.Thrown final Throwable thrown) {
      if (listener == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(MemcachedClient.class);

      listener.done(thrown);
    }
  }
}
