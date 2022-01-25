/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.reactor;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
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
import reactor.core.publisher.MonoSink;

public class KotlinMonoCoroutineInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf("kotlinx.coroutines.reactor.MonoCoroutine");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("kotlin.coroutines.CoroutineContext"))
                    .and(takesArgument(1, named("reactor.core.publisher.MonoSink")))),
        this.getClass().getName() + "$MonoCoroutineAdvice");
  }

  @SuppressWarnings("unused")
  public static class MonoCoroutineAdvice {

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(value = 0, readOnly = false) CoroutineContext coroutineContext,
        @Advice.Argument(1) MonoSink<?> monoSink) {
      Context context =
          ContextPropagationOperator.getOpenTelemetryContext(
              monoSink.currentContext(), Java8BytecodeBridge.currentContext());
      coroutineContext =
          KotlinCoroutinesInstrumentationHelper.addOpenTelemetryContext(coroutineContext, context);
    }
  }
}
