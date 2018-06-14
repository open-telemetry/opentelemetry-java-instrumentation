package datadog.trace.instrumentation.spymemcached;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClassWithMethod;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Method;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;

@AutoService(Instrumenter.class)
public final class MemcachedClientInstrumentation extends Instrumenter.Configurable {

  private static final String MEMCACHED_PACKAGE = "net.spy.memcached";
  private static final String HELPERS_PACKAGE =
      MemcachedClientInstrumentation.class.getPackage().getName();

  public static final HelperInjector HELPER_INJECTOR =
      new HelperInjector(
          HELPERS_PACKAGE + ".CompletionListener",
          HELPERS_PACKAGE + ".GetCompletionListener",
          HELPERS_PACKAGE + ".OperationCompletionListener",
          HELPERS_PACKAGE + ".BulkGetCompletionListener");

  public MemcachedClientInstrumentation() {
    super("spymemcached");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named(MEMCACHED_PACKAGE + ".MemcachedClient"),
            // Target 2.12 that has this method
            classLoaderHasClassWithMethod(
                MEMCACHED_PACKAGE + ".ConnectionFactoryBuilder",
                "setListenerExecutorService",
                "java.util.concurrent.ExecutorService"))
        .transform(HELPER_INJECTOR)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(returns(named(MEMCACHED_PACKAGE + ".internal.OperationFuture")))
                        /*
                        Flush seems to have a bug when listeners may not be always called.
                        Also tracing flush is probably of a very limited value.
                        */
                        .and(not(named("flush"))),
                    AsyncOperationAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(returns(named(MEMCACHED_PACKAGE + ".internal.GetFuture"))),
                    AsyncGetAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPublic())
                        .and(returns(named(MEMCACHED_PACKAGE + ".internal.BulkFuture"))),
                    AsyncBulkAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(isPublic()).and(named("incr").or(named("decr"))),
                    SyncOperationAdvice.class.getName()))
        .asDecorator();
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
      if (shouldInjectListener) {
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
      if (shouldInjectListener) {
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
      if (shouldInjectListener) {
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
