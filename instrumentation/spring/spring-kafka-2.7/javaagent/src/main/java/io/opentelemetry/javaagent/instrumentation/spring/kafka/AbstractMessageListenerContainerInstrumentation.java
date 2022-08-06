/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import static io.opentelemetry.javaagent.instrumentation.spring.kafka.SpringKafkaSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
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

  @SuppressWarnings({"PrivateConstructorForUtilityClass", "unused"})
  public static class GetBatchInterceptorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void onExit(
        @Advice.Return(readOnly = false) BatchInterceptor<K, V> interceptor) {

      if (interceptor == null
          || !interceptor
              .getClass()
              .getName()
              .equals(
                  "io.opentelemetry.instrumentation.spring.kafka.v2_7.InstrumentedBatchInterceptor")) {
        interceptor = telemetry().createBatchInterceptor(interceptor);
      }
    }
  }

  @SuppressWarnings({"PrivateConstructorForUtilityClass", "unused"})
  public static class GetRecordInterceptorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void onExit(
        @Advice.Return(readOnly = false) RecordInterceptor<K, V> interceptor) {

      if (interceptor == null
          || !interceptor
              .getClass()
              .getName()
              .equals(
                  "io.opentelemetry.instrumentation.spring.kafka.v2_7.InstrumentedRecordInterceptor")) {
        interceptor = telemetry().createRecordInterceptor(interceptor);
      }
    }
  }
}
