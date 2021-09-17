/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.StateHolder.HOLDER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class StreamTaskStopInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.StreamTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("process")),
        StreamTaskStopInstrumentation.class.getName() + "$StopSpanAdvice");
  }

  @SuppressWarnings("unused")
  public static class StopSpanAdvice {

    @Advice.OnMethodEnter
    public static StateHolder onEnter() {
      StateHolder holder = new StateHolder();
      HOLDER.set(holder);
      return holder;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter StateHolder holder, @Advice.Thrown Throwable throwable) {
      HOLDER.remove();

      Context context = holder.getContext();
      if (context != null) {
        holder.closeScope();
        instrumenter().end(context, holder.getRecord(), null, throwable);
      }
    }
  }
}
