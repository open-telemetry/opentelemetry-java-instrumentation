/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

public class AbstractMessageListenerContainerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.kafka.listener.AbstractMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // getBatchInterceptor() is called internally by AbstractMessageListenerContainer
    // implementations
    transformer.applyAdviceToMethod(
        named("getBatchInterceptor")
            .and(isProtected())
            .and(takesArguments(0))
            .and(returns(named("org.springframework.kafka.listener.BatchInterceptor"))),
        this.getClass().getName() + "$GetBatchInterceptorAdvice");
    // getRecordInterceptor() is called internally by AbstractMessageListenerContainer
    // implementations
    transformer.applyAdviceToMethod(
        named("getRecordInterceptor")
            .and(isProtected())
            .and(takesArguments(0))
            .and(returns(named("org.springframework.kafka.listener.RecordInterceptor"))),
        this.getClass().getName() + "$GetRecordInterceptorAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetBatchInterceptorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void onExit(
        @Advice.Return(readOnly = false) BatchInterceptor<K, V> interceptor) {

      if (!(interceptor instanceof InstrumentedBatchInterceptor)) {
        VirtualField<ConsumerRecords<K, V>, Context> receiveContextField =
            VirtualField.find(ConsumerRecords.class, Context.class);
        VirtualField<ConsumerRecords<K, V>, State<ConsumerRecords<K, V>>> stateField =
            VirtualField.find(ConsumerRecords.class, State.class);
        interceptor =
            new InstrumentedBatchInterceptor<>(receiveContextField, stateField, interceptor);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class GetRecordInterceptorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void onExit(
        @Advice.Return(readOnly = false) RecordInterceptor<K, V> interceptor) {

      if (!(interceptor instanceof InstrumentedRecordInterceptor)) {
        VirtualField<ConsumerRecord<K, V>, Context> receiveContextField =
            VirtualField.find(ConsumerRecord.class, Context.class);
        VirtualField<ConsumerRecord<K, V>, State<ConsumerRecord<K, V>>> stateField =
            VirtualField.find(ConsumerRecord.class, State.class);
        interceptor =
            new InstrumentedRecordInterceptor<>(receiveContextField, stateField, interceptor);
      }
    }
  }
}
