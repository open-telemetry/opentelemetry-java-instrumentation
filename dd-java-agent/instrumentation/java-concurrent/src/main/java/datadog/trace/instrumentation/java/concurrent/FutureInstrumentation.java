package datadog.trace.instrumentation.java.concurrent;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.java.concurrent.ExecutorInstrumentation.ConcurrentUtils;
import datadog.trace.instrumentation.java.concurrent.ExecutorInstrumentation.DatadogWrapper;
import java.util.concurrent.Future;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class FutureInstrumentation extends Instrumenter.Configurable {

  public FutureInstrumentation() {
    super(ExecutorInstrumentation.EXEC_NAME);
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named(Future.class.getName()))))
        .transform(ExecutorInstrumentation.EXEC_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("cancel").and(returns(boolean.class)),
                    CanceledFutureAdvice.class.getName()))
        .asDecorator();
  }

  public static class CanceledFutureAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static DatadogWrapper findWrapper(@Advice.This Future<?> future) {
      return ConcurrentUtils.getDatadogWrapper(future);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void abortTracing(
        @Advice.Enter final DatadogWrapper wrapper, @Advice.Return boolean canceled) {
      if (canceled && null != wrapper) {
        wrapper.cancel();
      }
    }
  }
}
