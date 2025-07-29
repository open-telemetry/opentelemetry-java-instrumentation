/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7.SpringKafkaSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.kafka.listener.RecordInterceptor;

public class AbstractMessageListenerContainerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.kafka.listener.AbstractMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // getRecordInterceptor() is called internally by AbstractMessageListenerContainer
    // implementations
    // for batch listeners we don't instrument getBatchInterceptor() here but instead instrument
    // KafkaMessageListenerContainer$ListenerConsumer because spring doesn't always call the success
    // and failure methods on a batch interceptor
    transformer.applyAdviceToMethod(
        named("getRecordInterceptor")
            .and(isProtected())
            .and(takesArguments(0))
            .and(returns(named("org.springframework.kafka.listener.RecordInterceptor"))),
        this.getClass().getName() + "$GetRecordInterceptorAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetRecordInterceptorAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> RecordInterceptor<K, V> onExit(
        @Advice.Return RecordInterceptor<K, V> originalInterceptor) {
      RecordInterceptor<K, V> interceptor = originalInterceptor;

      if (interceptor == null
          || !interceptor
              .getClass()
              .getName()
              .equals(
                  "io.opentelemetry.instrumentation.spring.kafka.v2_7.InstrumentedRecordInterceptor")) {
        interceptor = telemetry().createRecordInterceptor(interceptor);
      }
      return interceptor;
    }
  }
}
