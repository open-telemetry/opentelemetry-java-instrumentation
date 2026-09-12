/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;
import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.restore;
import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.suppress;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

class DirectMessageListenerContainerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // This method encloses consumer registration across supported versions.
    transformer.applyAdviceToMethod(
        named("doConsumeFromQueue").and(takesArguments(1).or(takesArguments(2))),
        getClass().getName() + "$ConsumeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static MessagingTelemetrySignals onEnter(
        @Advice.This AbstractMessageListenerContainer container) {
      if (SpringRabbitListenerUtil.shouldTraceListenerProcess(container)) {
        return suppress(PROCESS, SPAN);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable MessagingTelemetrySignals previous) {
      if (previous != null) {
        restore(previous);
      }
    }
  }
}
