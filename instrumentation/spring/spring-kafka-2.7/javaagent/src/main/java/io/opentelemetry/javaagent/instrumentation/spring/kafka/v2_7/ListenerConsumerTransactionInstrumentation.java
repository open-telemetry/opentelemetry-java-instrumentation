/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.spring.kafka.v2_7.internal.EarlyInterceptionCleanup;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.kafka.listener.RecordInterceptor;

// Spring Kafka 2.8+ ends the record processing span through the interceptor's afterRecord and
// clearThreadState callbacks. Before 2.8, when the record is intercepted before the transaction
// starts and the transaction fails to start, neither success nor failure is called, so the span
// started by the early interceptor is ended here instead.
class ListenerConsumerTransactionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    // added in 2.8.0
    return not(hasClassesNamed("org.springframework.kafka.listener.ThreadStateProcessor"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeInTransaction")
            .and(takesArguments(2))
            .and(takesArgument(0, named("java.util.Iterator")))
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.ConsumerRecord"))),
        getClass().getName() + "$InvokeInTransactionAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeInTransactionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(
        @Advice.FieldValue("earlyRecordInterceptor") @Nullable
            RecordInterceptor<?, ?> earlyRecordInterceptor) {
      if (earlyRecordInterceptor instanceof EarlyInterceptionCleanup) {
        return ((EarlyInterceptionCleanup) earlyRecordInterceptor).getActiveInvocation();
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.FieldValue("earlyRecordInterceptor") @Nullable
            RecordInterceptor<?, ?> earlyRecordInterceptor,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object invocation) {
      if (throwable != null
          && invocation != null
          && earlyRecordInterceptor instanceof EarlyInterceptionCleanup) {
        ((EarlyInterceptionCleanup) earlyRecordInterceptor)
            .endActiveInvocation(invocation, throwable);
      }
    }
  }
}
