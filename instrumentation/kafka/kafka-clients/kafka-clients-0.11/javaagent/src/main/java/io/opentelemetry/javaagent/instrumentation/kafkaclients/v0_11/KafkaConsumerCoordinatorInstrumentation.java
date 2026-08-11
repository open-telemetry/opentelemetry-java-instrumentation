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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

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
        getClass().getName() + "$CommitOffsetsAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class CommitOffsetsAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) Map<?, ?> offsets) {
      KafkaCommitAsyncTracing.trackOffsets(offsets);
    }
  }
}
