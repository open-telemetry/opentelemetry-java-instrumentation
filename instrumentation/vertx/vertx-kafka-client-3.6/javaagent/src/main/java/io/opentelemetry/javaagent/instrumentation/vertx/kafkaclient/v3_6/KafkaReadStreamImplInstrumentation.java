/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafkaclient.v3_6;

import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.Handler;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

class KafkaReadStreamImplInstrumentation implements TypeInstrumentation {

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
        getClass().getName() + "$HandlerAdvice");
    transformer.applyAdviceToMethod(
        named("batchHandler")
            .and(takesArguments(1))
            .and(takesArgument(0, named("io.vertx.core.Handler"))),
        getClass().getName() + "$BatchHandlerAdvice");
    transformer.applyAdviceToMethod(
        named("run").and(isPrivate()), getClass().getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static <K, V> Handler<ConsumerRecord<K, V>> onEnter(
        @Advice.Argument(0) @Nullable Handler<ConsumerRecord<K, V>> handler) {

      if (handler == null) {
        return null;
      }
      return new InstrumentedSingleRecordHandler<>(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class BatchHandlerAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static <K, V> Handler<ConsumerRecords<K, V>> onEnter(
        @Advice.Argument(0) @Nullable Handler<ConsumerRecords<K, V>> handler) {

      if (handler == null) {
        return null;
      }
      return new InstrumentedBatchRecordsHandler<>(handler);
    }
  }

  // this advice suppresses the CONSUMER spans created by the kafka-clients instrumentation
  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter() {
      return KafkaClientsConsumerProcessTracing.setEnabled(false);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter boolean previousValue) {
      KafkaClientsConsumerProcessTracing.setEnabled(previousValue);
    }
  }
}
