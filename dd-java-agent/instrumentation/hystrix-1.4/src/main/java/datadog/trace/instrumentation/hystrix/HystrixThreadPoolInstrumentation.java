package datadog.trace.instrumentation.hystrix;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class HystrixThreadPoolInstrumentation extends Instrumenter.Configurable {

  public HystrixThreadPoolInstrumentation() {
    super("hystrix");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named(
                "com.netflix.hystrix.strategy.concurrency.HystrixContextScheduler$ThreadPoolWorker"),
            classLoaderHasClasses("com.netflix.hystrix.AbstractCommand"))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod().and(named("schedule")).and(takesArguments(1)),
                    EnableAsyncAdvice.class.getName()))
        .asDecorator();
  }

  public static class EnableAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean enableAsyncTracking() {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope) {
        if (!((TraceScope) scope).isAsyncPropagating()) {
          ((TraceScope) scope).setAsyncPropagation(true);
          return true;
        }
      }
      return false;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void disableAsyncTracking(@Advice.Enter final boolean wasEnabled) {
      if (wasEnabled) {
        final Scope scope = GlobalTracer.get().scopeManager().active();
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(false);
        }
      }
    }
  }
}
