/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation is responsible for suppressing the underlying Kafka client consumer spans to
 * avoid duplicate telemetry. Without this suppression, both high-level Kafka Connect spans (from
 * {@link SinkTaskInstrumentation}) and low-level kafka-clients spans would be created for the same
 * consumer operation. This ensures only the meaningful Kafka Connect spans are generated.
 */
public class WorkerSinkTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.connect.runtime.WorkerSinkTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument the execute method which contains the main polling loop
    transformer.applyAdviceToMethod(named("execute"), this.getClass().getName() + "$ExecuteAdvice");
  }

  // This advice suppresses the CONSUMER spans created by the kafka-clients instrumentation
  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

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
