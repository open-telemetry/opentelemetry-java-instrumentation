/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;

class KafkaConsumerCoordinatorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.internals.ConsumerCoordinator");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("commitOffsetsAsync")
            .and(takesArguments(2))
            .and(takesArgument(0, Map.class))
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.OffsetCommitCallback"))),
        getClass().getName() + "$WrapCallbackAdvice");
    transformer.applyAdviceToMethod(
        named("doCommitOffsetsAsync")
            .and(takesArguments(2))
            .and(takesArgument(0, Map.class))
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.OffsetCommitCallback"))),
        getClass().getName() + "$UseDefaultCallbackAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapCallbackAdvice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static OffsetCommitCallback onEnter(
        @Advice.Argument(1) @Nullable OffsetCommitCallback callback) {
      return KafkaCommitAsyncTracing.wrapCallbackOrCompletion(callback);
    }
  }

  @SuppressWarnings("unused")
  public static class UseDefaultCallbackAdvice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static OffsetCommitCallback onEnter(
        @Advice.Argument(1) @Nullable OffsetCommitCallback callback,
        @Advice.FieldValue("defaultOffsetCommitCallback")
            OffsetCommitCallback defaultOffsetCommitCallback) {
      return KafkaCommitAsyncTracing.useDefaultCallback(callback, defaultOffsetCommitCallback);
    }
  }
}
