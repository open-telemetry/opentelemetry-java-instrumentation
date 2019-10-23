package datadog.trace.instrumentation.reactor.core;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import reactor.core.CoreSubscriber;

/**
 * Instruments Flux#subscribe(CoreSubscriber) and Mono#subscribe(CoreSubscriber). It looks like Mono
 * and Flux implementations tend to do a lot of interesting work on subscription.
 *
 * <p>This instrumentation is similar to java-concurrent instrumentation in a sense that it doesn't
 * create any new spans. Instead it makes sure that existing span is propagated through Flux/Mono
 * execution.
 */
public class FluxAndMonoSubscribeAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope methodEnter(
      @Advice.Argument(0) final CoreSubscriber subscriber, @Advice.This final Object thiz) {
    final AgentSpan span =
        subscriber
            .currentContext()
            .getOrDefault(ReactorCoreAdviceUtils.PUBLISHER_CONTEXT_KEY, null);
    if (span != null) {
      final AgentScope scope = activateSpan(span, false);
      scope.setAsyncPropagation(true);
      return scope;
    }
    return null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
    if (throwable != null) {
      ReactorCoreAdviceUtils.finishSpanIfPresent(scope.span(), throwable);
    }
    if (scope != null) {
      scope.close();
    }
  }
}
