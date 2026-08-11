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
        getClass().getName() + "$CommitOffsetsAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class CommitOffsetsAsyncAdvice {

    @AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static OffsetCommitCallback onEnter(
        @Advice.Argument(1) @Nullable OffsetCommitCallback callback,
        @Advice.FieldValue("defaultOffsetCommitCallback")
            OffsetCommitCallback defaultOffsetCommitCallback) {
      return KafkaCommitAsyncTracing.wrapCallback(
          callback == null ? defaultOffsetCommitCallback : callback);
    }
  }
}
