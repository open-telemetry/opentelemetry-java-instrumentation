/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.connect.sink.SinkRecord;

class WorkerSinkTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.connect.runtime.WorkerSinkTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pollConsumer")
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecords"))),
        getClass().getName() + "$PollConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("convertAndTransformRecord")
            .and(takesArgument(0, named("org.apache.kafka.clients.consumer.ConsumerRecord"))),
        getClass().getName() + "$ConvertAndTransformRecordArgumentZeroAdvice");
    transformer.applyAdviceToMethod(
        named("convertAndTransformRecord")
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.ConsumerRecord"))),
        getClass().getName() + "$ConvertAndTransformRecordArgumentOneAdvice");
  }

  @SuppressWarnings("unused")
  public static class PollConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter() {
      return KafkaClientsConsumerProcessTracing.setWrappingEnabled(false);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter boolean previousValue,
        @Advice.Return @Nullable ConsumerRecords<?, ?> records) {
      KafkaClientsConsumerProcessTracing.setWrappingEnabled(previousValue);
      if (records != null) {
        KafkaConnectBatchState.claimProcessSpan(records);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConvertAndTransformRecordArgumentZeroAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) ConsumerRecord<?, ?> source,
        @Advice.Return @Nullable SinkRecord transformedRecord) {
      KafkaConnectTask.copyReceiveOperation(source, transformedRecord);
    }
  }

  @SuppressWarnings("unused")
  public static class ConvertAndTransformRecordArgumentOneAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) ConsumerRecord<?, ?> source,
        @Advice.Return @Nullable SinkRecord transformedRecord) {
      KafkaConnectTask.copyReceiveOperation(source, transformedRecord);
    }
  }
}
