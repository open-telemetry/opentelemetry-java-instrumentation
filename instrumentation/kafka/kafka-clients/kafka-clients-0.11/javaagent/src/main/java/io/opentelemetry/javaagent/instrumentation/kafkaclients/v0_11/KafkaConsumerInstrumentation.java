/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.CONSUMED_MESSAGES;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Duration;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

class KafkaConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.KafkaConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("poll")
            .and(isPublic())
            .and(takesArguments(1))
            .and(takesArgument(0, long.class).or(takesArgument(0, Duration.class)))
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecords"))),
        getClass().getName() + "$PollAdvice");
  }

  @SuppressWarnings("unused")
  public static class PollAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Timer timer,
        @Advice.This Consumer<?, ?> consumer,
        @Advice.Return @Nullable ConsumerRecords<?, ?> records,
        @Advice.Thrown @Nullable Throwable error) {

      // don't create spans when no records were received
      if (records == null || records.isEmpty()) {
        return;
      }

      Context parentContext = KafkaConsumerContextUtil.withoutLeakedProcessSpan(currentContext());
      KafkaReceiveRequest request = KafkaReceiveRequest.create(records, consumer);

      // disable process tracing and store the receive span for each individual record too
      boolean previousValue = KafkaClientsConsumerProcessTracing.setWrappingEnabled(false);
      try {
        Context receiveContext = null;
        boolean receiveOperationStarted = false;
        if (consumerReceiveInstrumenter().shouldStart(parentContext, request)) {
          receiveContext =
              InstrumenterUtil.startAndEnd(
                  consumerReceiveInstrumenter(),
                  parentContext,
                  request,
                  null,
                  error,
                  timer.startTime(),
                  timer.now());
          receiveOperationStarted = true;
        }

        Context processParentContext =
            emitStableMessagingSemconv()
                ? KafkaConsumerContextUtil.withReceiveOperation(
                    parentContext, receiveOperationStarted)
                : receiveContext;
        KafkaConsumerContext consumerContext =
            KafkaConsumerContextUtil.create(processParentContext, consumer);
        // we're attaching the consumer to the records to be able to retrieve things like consumer
        // group or clientId later
        KafkaConsumerContextUtil.set(records, consumerContext);

        for (ConsumerRecord<?, ?> record : records) {
          KafkaConsumerContextUtil.set(record, consumerContext);
          // the receive span covers the whole batch rather than this record, so only the counter
          // is claimed here
          if (receiveOperationStarted && emitStableMessagingSemconv()) {
            MessagingTelemetryCarrier<ConsumerRecord<?, ?>> recordTelemetry =
                MessagingTelemetryCarrier.create(
                    VirtualField.find(ConsumerRecord.class, MessagingTelemetryClaims.class));
            recordTelemetry.claim(record, RECEIVE, CONSUMED_MESSAGES);
          }
        }
      } finally {
        KafkaClientsConsumerProcessTracing.setWrappingEnabled(previousValue);
      }
    }
  }
}
