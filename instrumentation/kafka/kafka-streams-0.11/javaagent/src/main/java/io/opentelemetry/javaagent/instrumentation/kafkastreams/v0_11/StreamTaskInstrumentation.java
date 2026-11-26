/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11.KafkaStreamsSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11.StateHolder.holder;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class StreamTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.StreamTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process").and(isPublic()), getClass().getName() + "$ProcessAdvice");
  }

  // the method decorated by this advice calls PartitionGroup.nextRecord(), which triggers
  // PartitionGroupInstrumentation that actually starts the span
  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static StateHolder onEnter() {
      StateHolder holder = new StateHolder();
      holder().set(holder);
      return holder;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Enter @Nullable StateHolder stateHolder,
        @Advice.Thrown @Nullable Throwable throwable) {
      holder().remove();
      if (stateHolder == null) {
        return;
      }

      Context context = stateHolder.getContext();
      if (context != null) {
        stateHolder.closeScope();
        instrumenter().end(context, stateHolder.getRequest(), null, throwable);
      }
    }
  }
}
