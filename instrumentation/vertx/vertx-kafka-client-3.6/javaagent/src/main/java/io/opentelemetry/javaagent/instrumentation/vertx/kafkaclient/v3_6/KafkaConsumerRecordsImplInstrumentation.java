/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafkaclient.v3_6;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class KafkaConsumerRecordsImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.kafka.client.consumer.impl.KafkaConsumerRecordsImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("recordAt")
            .and(isPublic())
            .and(takesArguments(int.class))
            .and(returns(named("io.vertx.kafka.client.consumer.KafkaConsumerRecord"))),
        getClass().getName() + "$RecordAtAdvice");
  }

  @SuppressWarnings("unused")
  public static class RecordAtAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static MessagingTelemetrySignals onEnter() {
      return KafkaClientsConsumerProcessTracing.suppress(PROCESS, SPAN);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter MessagingTelemetrySignals previous) {
      KafkaClientsConsumerProcessTracing.restore(previous);
    }
  }
}
