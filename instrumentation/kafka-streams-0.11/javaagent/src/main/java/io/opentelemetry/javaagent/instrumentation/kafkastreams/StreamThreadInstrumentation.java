/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerIteratorWrapper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaTracingWrapperUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

// This instrumentation copies the receive CONSUMER span context from the ConsumerRecords aggregate
// object to each individual record
public class StreamThreadInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.StreamThread");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pollRequests")
            .and(isPrivate())
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecords"))),
        this.getClass().getName() + "$PollRecordsAdvice");
    transformer.applyAdviceToMethod(named("runLoop"), this.getClass().getName() + "$RunLoopAdvice");
  }

  @SuppressWarnings("unused")
  public static class PollRecordsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return ConsumerRecords<?, ?> records) {
      if (records.isEmpty()) {
        return;
      }

      SpanContext receiveSpanContext =
          VirtualField.find(ConsumerRecords.class, SpanContext.class).get(records);
      if (receiveSpanContext == null) {
        return;
      }

      VirtualField<ConsumerRecord, SpanContext> singleRecordReceiveSpan =
          VirtualField.find(ConsumerRecord.class, SpanContext.class);

      for (ConsumerRecord<?, ?> record : records) {
        singleRecordReceiveSpan.set(record, receiveSpanContext);
      }
    }
  }

  // this advice suppresses the CONSUMER spans created by the kafka-clients instrumentation
  @SuppressWarnings("unused")
  public static class RunLoopAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      KafkaTracingWrapperUtil.disableWrapping();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit() {
      KafkaTracingWrapperUtil.enableWrapping();
    }
  }
}
