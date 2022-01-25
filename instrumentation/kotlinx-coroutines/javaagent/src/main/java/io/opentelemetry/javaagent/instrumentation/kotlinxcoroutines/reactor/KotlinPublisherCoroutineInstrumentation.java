/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.reactor;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.ContextPropagationOperator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.KotlinCoroutinesInstrumentationHelper;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscriber;
import reactor.core.CoreSubscriber;

public class KotlinPublisherCoroutineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("kotlinx.coroutines.reactive.PublisherCoroutine");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("kotlin.coroutines.CoroutineContext"))
                    .and(takesArgument(1, named("org.reactivestreams.Subscriber")))),
        this.getClass().getName() + "$PublisherCoroutineAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublisherCoroutineAdvice {

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false) CoroutineContext coroutineContext,
        @Advice.Argument(1) Subscriber<?> subscriber) {
      if (subscriber instanceof CoreSubscriber) {
        CoreSubscriber<?> coreSubscriber = (CoreSubscriber) subscriber;
        Context context =
            ContextPropagationOperator.getOpenTelemetryContext(
                coreSubscriber.currentContext(), Java8BytecodeBridge.currentContext());
        coroutineContext =
            KotlinCoroutinesInstrumentationHelper.addOpenTelemetryContext(
                coroutineContext, context);
      }
    }
  }
}
