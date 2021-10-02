/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.KafkaStreamsSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.kafkastreams.StateHolder.HOLDER;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerIterableWrapper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class StreamTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.StreamTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("process").and(isPublic()),
        StreamTaskInstrumentation.class.getName() + "$ProcessAdvice");
    transformer.applyAdviceToMethod(
        named("addRecords").and(isPublic()).and(takesArgument(1, Iterable.class)),
        StreamTaskInstrumentation.class.getName() + "$AddRecordsAdvice");
  }

  // the method decorated by this advice calls PartitionGroup.nextRecord(), which triggers
  // PartitionGroupInstrumentation that actually starts the span
  @SuppressWarnings("unused")
  public static class ProcessAdvice {

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

  // this advice removes the CONSUMER spans created by the kafka-clients instrumentation
  @SuppressWarnings("unused")
  public static class AddRecordsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 1, readOnly = false)
            Iterable<? extends ConsumerRecord<?, ?>> records) {

      // this will forcefully suppress the kafka-clients CONSUMER instrumentation even though
      // there's no current CONSUMER span
      if (records instanceof KafkaConsumerIterableWrapper) {
        records = ((KafkaConsumerIterableWrapper<?, ?>) records).unwrap();
      }
    }
  }
}
