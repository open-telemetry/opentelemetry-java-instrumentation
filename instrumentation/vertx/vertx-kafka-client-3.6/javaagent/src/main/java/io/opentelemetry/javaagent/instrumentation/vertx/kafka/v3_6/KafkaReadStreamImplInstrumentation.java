/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import io.vertx.kafka.client.consumer.impl.KafkaReadStreamImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V> Handler<ConsumerRecord<K, V>> onEnter(
        @Advice.This KafkaReadStreamImpl<K, V> readStream,
        @Advice.Argument(0) Handler<ConsumerRecord<K, V>> handler) {

      return new InstrumentedSingleRecordHandler<>(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class BatchHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V> Handler<ConsumerRecords<K, V>> onEnter(
        @Advice.This KafkaReadStreamImpl<K, V> readStream,
        @Advice.Argument(0) Handler<ConsumerRecords<K, V>> handler) {

      return new InstrumentedBatchRecordsHandler<>(handler);
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
