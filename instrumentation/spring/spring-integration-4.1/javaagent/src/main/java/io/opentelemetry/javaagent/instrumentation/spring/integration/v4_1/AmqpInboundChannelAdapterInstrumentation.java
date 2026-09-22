/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;
import org.springframework.messaging.support.ExecutorChannelInterceptor;

class AmqpInboundChannelAdapterInstrumentation implements TypeInstrumentation {

  private static final String ADAPTER =
      "org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith(ADAPTER + "$")
        .and(
            hasSuperType(
                named("org.springframework.amqp.rabbit.core.ChannelAwareMessageListener")
                    .or(
                        named(
                            "org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener"))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage")
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.springframework.amqp.core.Message")))
            .and(takesArgument(1, named("com.rabbitmq.client.Channel"))),
        getClass().getName() + "$OnMessageAdvice");
    transformer.applyAdviceToMethod(none(), getClass().getName() + "$MuzzleAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Object onEnter(@Advice.Argument(0) Message message) {
      return SpringRabbitProcessingContext.enter(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Object state) {
      SpringRabbitProcessingContext.exit(state);
    }
  }

  @SuppressWarnings("unused")
  public static class MuzzleAdvice {
    @Advice.OnMethodExit(inline = false)
    public static void onExit() {
      // The AMQP handoff is consumed by the Spring Integration interceptor, which implements this
      // 4.1 API. Name-based class-loader matchers are not collected as Muzzle references.
      throw new UnsupportedOperationException(
          ExecutorChannelInterceptor.class.getName()
              + " referencing for muzzle, should never be actually called");
    }
  }
}
