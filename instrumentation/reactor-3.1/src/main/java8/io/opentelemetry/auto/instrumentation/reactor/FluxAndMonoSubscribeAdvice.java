/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.reactor;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
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
  public static SpanWithScope methodEnter(
      @Advice.Argument(0) final CoreSubscriber subscriber, @Advice.This final Object thiz) {
    final Span span =
        subscriber
            .currentContext()
            .getOrDefault(ReactorCoreAdviceUtils.PUBLISHER_CONTEXT_KEY, null);
    if (span != null) {
      return new SpanWithScope(span, currentContextWith(span));
    }
    return null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }
    if (throwable != null) {
      ReactorCoreAdviceUtils.finishSpanIfPresent(spanWithScope.getSpan(), throwable);
    }
    spanWithScope.closeScope();
  }
}
