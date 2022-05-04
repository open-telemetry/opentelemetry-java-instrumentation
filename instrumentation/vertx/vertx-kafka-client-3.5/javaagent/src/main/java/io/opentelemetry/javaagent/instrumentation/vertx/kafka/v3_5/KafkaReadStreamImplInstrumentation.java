/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_5;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class KafkaReadStreamImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.kafka.client.consumer.impl.KafkaReadStreamImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("handler")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$HandlerAdvice");
    transformer.applyAdviceToMethod(
        named("batchHandler")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        this.getClass().getName() + "$BatchHandlerAdvice");
    transformer.applyAdviceToMethod(
        named("run").and(isPrivate()), this.getClass().getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V> void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Handler<ConsumerRecord<K, V>> handler) {

      VirtualField<ConsumerRecord<K, V>, Context> receiveContextField =
          VirtualField.find(ConsumerRecord.class, Context.class);
      handler = new InstrumentedSingleRecordHandler<>(receiveContextField, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class BatchHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V> void onEnter(
        @Advice.Argument(value = 0, readOnly = false) Handler<ConsumerRecords<K, V>> handler) {
      // TODO: next PR
    }
  }

  // this advice suppresses the CONSUMER spans created by the kafka-clients instrumentation
  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean onEnter() {
      return KafkaClientsConsumerProcessTracing.setEnabled(false);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter boolean previousValue) {
      KafkaClientsConsumerProcessTracing.setEnabled(previousValue);
    }
  }
}
