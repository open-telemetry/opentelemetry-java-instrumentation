/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class SimpleMessageListenerContainerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createBlockingQueueConsumer").and(takesArguments(0)),
        getClass().getName() + "$CreateConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("setConsumerBatchEnabled").and(takesArguments(boolean.class)),
        getClass().getName() + "$SetConsumerBatchEnabledAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateConsumerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Return @Nullable BlockingQueueConsumer consumer) {
      if (consumer != null && SpringRabbitListenerUtil.hasSingleMessageDelivery(container)) {
        SpringRabbitListenerUtil.markSpringListenerConsumer(consumer);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SetConsumerBatchEnabledAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This SimpleMessageListenerContainer container,
        @Advice.Argument(0) boolean enabled) {
      SpringRabbitListenerUtil.setConsumerBatchEnabled(container, enabled);
    }
  }
}
