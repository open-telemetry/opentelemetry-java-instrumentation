/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanSuppression;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaProcessingSelectionUtil.selectFrameworkProcessing;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecords;

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
  }

  @SuppressWarnings("unused")
  public static class PollConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter() {
      return processSpanSuppression().tryAcquire();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter boolean suppressionAcquired,
        @Advice.Return @Nullable ConsumerRecords<?, ?> records) {
      if (suppressionAcquired) {
        processSpanSuppression().release();
      }
      if (records != null) {
        selectFrameworkProcessing(records);
      }
    }
  }
}
