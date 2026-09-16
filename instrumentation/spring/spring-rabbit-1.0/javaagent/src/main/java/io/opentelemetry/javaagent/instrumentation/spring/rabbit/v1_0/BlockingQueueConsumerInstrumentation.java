/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.bootstrap.rabbitmq.RabbitMqConsumerProcessTracing.currentProcessSpanSuppression;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;

class BlockingQueueConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.amqp.rabbit.listener.BlockingQueueConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("consumeFromQueue").and(takesArguments(String.class)),
        getClass().getName() + "$ConsumerRegistrationAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumerRegistrationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static boolean onEnter(@Advice.This BlockingQueueConsumer consumer) {
      if (!SpringRabbitListenerUtil.isSpringListenerConsumer(consumer)
          || currentProcessSpanSuppression().get() != null) {
        return false;
      }
      currentProcessSpanSuppression().set(Boolean.TRUE);
      return true;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter boolean installed) {
      if (installed) {
        currentProcessSpanSuppression().restore(null);
      }
    }
  }
}
