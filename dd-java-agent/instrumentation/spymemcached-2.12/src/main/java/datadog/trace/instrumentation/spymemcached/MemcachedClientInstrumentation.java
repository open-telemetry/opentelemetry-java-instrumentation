package datadog.trace.instrumentation.spymemcached;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithMethod;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

@AutoService(Instrumenter.class)
public final class MemcachedClientInstrumentation extends Instrumenter.Default {

  private static final String MEMCACHED_PACKAGE = "net.spy.memcached";
  private static final String HELPERS_PACKAGE =
      MemcachedClientInstrumentation.class.getPackage().getName();

  public static final HelperInjector HELPER_INJECTOR = new HelperInjector();

  public MemcachedClientInstrumentation() {
    super("spymemcached");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return named(MEMCACHED_PACKAGE + ".MemcachedClient");
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    // Target 2.12 that has this method
    return classLoaderHasClassWithMethod(
        MEMCACHED_PACKAGE + ".ConnectionFactoryBuilder",
        "setListenerExecutorService",
        "java.util.concurrent.ExecutorService");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      HELPERS_PACKAGE + ".CompletionListener",
      HELPERS_PACKAGE + ".SyncCompletionListener",
      HELPERS_PACKAGE + ".GetCompletionListener",
      HELPERS_PACKAGE + ".OperationCompletionListener",
      HELPERS_PACKAGE + ".BulkGetCompletionListener"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    Map<ElementMatcher, String> transformers = new HashMap<>();
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

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  public static class AsyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean methodEnter() {
      return CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final boolean shouldInjectListener,
        @Advice.Origin final Method method,
        @Advice.Return final OperationFuture future) {
      if (shouldInjectListener && future != null) {
        OperationCompletionListener listener =
            new OperationCompletionListener(GlobalTracer.get(), method.getName());
        future.addListener(listener);
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
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
        @Advice.Origin final Method method,
        @Advice.Return final GetFuture future) {
      if (shouldInjectListener && future != null) {
        GetCompletionListener listener =
            new GetCompletionListener(GlobalTracer.get(), method.getName());
        future.addListener(listener);
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
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
        @Advice.Origin final Method method,
        @Advice.Return final BulkFuture future) {
      if (shouldInjectListener && future != null) {
        BulkGetCompletionListener listener =
            new BulkGetCompletionListener(GlobalTracer.get(), method.getName());
        future.addListener(listener);
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
      }
    }
  }

  public static class SyncOperationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SyncCompletionListener methodEnter(@Advice.Origin final Method method) {
      if (CallDepthThreadLocalMap.incrementCallDepth(MemcachedClient.class) <= 0) {
        return new SyncCompletionListener(GlobalTracer.get(), method.getName());
      } else {
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SyncCompletionListener listener,
        @Advice.Thrown final Throwable thrown) {
      if (listener != null) {
        listener.done(thrown);
        CallDepthThreadLocalMap.reset(MemcachedClient.class);
      }
    }
  }
}
