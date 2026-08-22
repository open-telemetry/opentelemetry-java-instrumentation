/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.isWrappingEnabled;
import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.setWrappingEnabled;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
    // This is the narrowest method that contains the basicConsume call across supported versions.
    transformer.applyAdviceToMethod(
        named("consume").and(takesArguments(2).or(takesArguments(3))),
        getClass().getName() + "$ConsumeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter(@Advice.This AbstractMessageListenerContainer container) {
      boolean previous = isWrappingEnabled();
      if (SpringRabbitListenerUtil.hasSingleMessageDelivery(container)) {
        setWrappingEnabled(false);
      }
      return previous;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter boolean previous) {
      setWrappingEnabled(previous);
    }
  }
}
