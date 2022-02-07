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
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.kafka.listener.BatchInterceptor;

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
  }

  @SuppressWarnings("unused")
  public static class GetBatchInterceptorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void onExit(
        @Advice.Return(readOnly = false) BatchInterceptor<K, V> interceptor) {
      if (!(interceptor instanceof InstrumentedBatchInterceptor)) {
        VirtualField<ConsumerRecords<K, V>, Context> receiveContextVirtualField =
            VirtualField.find(ConsumerRecords.class, Context.class);
        VirtualField<ConsumerRecords<K, V>, State<K, V>> stateStore =
            VirtualField.find(ConsumerRecords.class, State.class);
        interceptor =
            new InstrumentedBatchInterceptor<>(receiveContextVirtualField, stateStore, interceptor);
      }
    }
  }
}
