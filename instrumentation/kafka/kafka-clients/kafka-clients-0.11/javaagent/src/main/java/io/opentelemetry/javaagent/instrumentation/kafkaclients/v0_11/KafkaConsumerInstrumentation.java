/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerCommitInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaCommitRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
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
import org.apache.kafka.clients.consumer.KafkaConsumer;

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
    transformer.applyAdviceToMethod(
        named("commitSync").and(isPublic()).and(returns(void.class)),
        getClass().getName() + "$CommitSyncAdvice");
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
        }

        Context processParentContext =
            emitStableMessagingSemconv() ? parentContext : receiveContext;
        // we're attaching the consumer to the records to be able to retrieve things like consumer
        // group or clientId later
        KafkaConsumerContextUtil.set(records, processParentContext, consumer);

        for (ConsumerRecord<?, ?> record : records) {
          KafkaConsumerContextUtil.set(record, processParentContext, consumer);
        }
      } finally {
        KafkaClientsConsumerProcessTracing.setWrappingEnabled(previousValue);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CommitSyncAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      @Nullable private final TracingState tracingState;

      private AdviceScope(CallDepth callDepth, @Nullable TracingState tracingState) {
        this.callDepth = callDepth;
        this.tracingState = tracingState;
      }

      public static AdviceScope start(@Nullable Object argument) {
        CallDepth callDepth = CallDepth.forClass(KafkaConsumer.class);
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null);
        }

        KafkaCommitRequest request = KafkaCommitRequest.create(argument);
        Context parentContext = Context.current();
        if (!consumerCommitInstrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, null);
        }

        Context context = consumerCommitInstrumenter().start(parentContext, request);
        return new AdviceScope(
            callDepth, new TracingState(context, context.makeCurrent(), request));
      }

      public void end(@Nullable Throwable error) {
        if (callDepth.decrementAndGet() > 0 || tracingState == null) {
          return;
        }

        tracingState.scope.close();
        consumerCommitInstrumenter().end(tracingState.context, tracingState.request, null, error);
      }
    }

    static class TracingState {
      final Context context;
      final Scope scope;
      final KafkaCommitRequest request;

      TracingState(Context context, Scope scope, KafkaCommitRequest request) {
        this.context = context;
        this.scope = scope;
        this.request = request;
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.Argument(value = 0, optional = true) @Nullable Object argument) {
      return AdviceScope.start(argument);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable error, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(error);
    }
  }
}
