/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_5;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class KafkaConsumerRecordsImplInstrumentation implements TypeInstrumentation {

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
        this.getClass().getName() + "$RecordAtAdvice");
  }

  @SuppressWarnings("unused")
  public static class RecordAtAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean onEnter() {
      return KafkaClientsConsumerProcessTracing.setEnabled(false);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter boolean previousValue) {
      KafkaClientsConsumerProcessTracing.setEnabled(previousValue);
    }
  }
}
