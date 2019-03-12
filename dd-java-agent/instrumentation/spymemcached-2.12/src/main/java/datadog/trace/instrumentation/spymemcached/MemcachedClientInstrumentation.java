package datadog.trace.instrumentation.spymemcached;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.DatabaseClientDecorator",
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
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(isPublic())
            .and(returns(named(MEMCACHED_PACKAGE + ".internal.OperationFuture")))
            /*
            Flush seems to have a bug when listeners may not be always called.
            Also tracing flush is probably of a very limited value.
            */
            .and(not(named("flush"))),
        AsyncOperationAdvice.class.getName());
    transformers.put(
        isMethod().and(isPublic()).and(returns(named(MEMCACHED_PACKAGE + ".internal.GetFuture"))),
        AsyncGetAdvice.class.getName());
    transformers.put(
        isMethod().and(isPublic()).and(returns(named(MEMCACHED_PACKAGE + ".internal.BulkFuture"))),
        AsyncBulkAdvice.class.getName());
    transformers.put(
        isMethod().and(isPublic()).and(named("incr").or(named("decr"))),
        SyncOperationAdvice.class.getName());
    return transformers;
  }

  public static class AsyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final boolean shouldInjectListener,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final OperationFuture future) {
      if (shouldInjectListener && future != null) {
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
        final OperationCompletionListener listener =
            new OperationCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class AsyncGetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final boolean shouldInjectListener,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final GetFuture future) {
      if (shouldInjectListener && future != null) {
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
        final GetCompletionListener listener =
            new GetCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class AsyncBulkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final boolean shouldInjectListener,
        @Advice.This final MemcachedClient client,
        @Advice.Origin("#m") final String methodName,
        @Advice.Return final BulkFuture future) {
      if (shouldInjectListener && future != null) {
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
        final BulkGetCompletionListener listener =
            new BulkGetCompletionListener(client.getConnection(), methodName);
        future.addListener(listener);
      }
    }
  }

  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SyncCompletionListener methodEnter(
        @Advice.This final MemcachedClient client, @Advice.Origin("#m") final String methodName) {
      if (CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0) {
        return new SyncCompletionListener(client.getConnection(), methodName);
      } else {
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SyncCompletionListener listener,
        @Advice.Thrown final Throwable thrown) {
      if (listener != null) {
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
        listener.done(thrown);
      }
    }
  }
}
