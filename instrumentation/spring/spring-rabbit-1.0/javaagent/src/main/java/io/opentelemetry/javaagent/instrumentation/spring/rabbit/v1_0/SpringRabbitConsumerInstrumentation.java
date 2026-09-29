/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;

class SpringRabbitConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.springframework.amqp.rabbit.listener.BlockingQueueConsumer$InternalConsumer",
        "org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer$SimpleConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Consumer consumer, @Advice.Argument(0) Object owner) {
      boolean processingOwnedOutsideRabbitClient =
          owner instanceof BlockingQueueConsumer
              ? SpringRabbitListenerUtil.springRabbitOwnsProcessing((BlockingQueueConsumer) owner)
              : owner instanceof AbstractMessageListenerContainer
                  && SpringRabbitListenerUtil.canTraceListenerProcessing(
                      (AbstractMessageListenerContainer) owner);
      SpringRabbitListenerUtil.setProcessingOwnedOutsideRabbitClient(
          consumer, processingOwnedOutsideRabbitClient);
    }
  }
}
