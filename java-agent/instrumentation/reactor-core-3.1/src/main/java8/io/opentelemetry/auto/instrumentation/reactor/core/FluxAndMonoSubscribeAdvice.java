package io.opentelemetry.auto.instrumentation.reactor.core;

import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;
import reactor.core.CoreSubscriber;

import static io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreDecorator.TRACER;

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
  public static SpanScopePair methodEnter(
      @Advice.Argument(0) final CoreSubscriber subscriber, @Advice.This final Object thiz) {
    final Span span =
        subscriber
            .currentContext()
            .getOrDefault(ReactorCoreAdviceUtils.PUBLISHER_CONTEXT_KEY, null);
    if (span != null) {
      return new SpanScopePair(span, TRACER.withSpan(span));
    }
    return null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final SpanScopePair scope, @Advice.Thrown final Throwable throwable) {
    if (throwable != null) {
      ReactorCoreAdviceUtils.finishSpanIfPresent(scope.getSpan(), throwable);
    }
    if (scope != null && scope.getScope() != null) {
      scope.getScope().close();
    }
  }
}
