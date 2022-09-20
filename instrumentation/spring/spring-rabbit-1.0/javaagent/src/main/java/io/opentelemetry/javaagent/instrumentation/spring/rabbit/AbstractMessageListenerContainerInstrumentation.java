/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit;

import static io.opentelemetry.javaagent.instrumentation.spring.rabbit.SpringRabbitSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.rabbitmq.client.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

public class AbstractMessageListenerContainerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeListener")
            .and(
                takesArguments(2)
                    .and(
                        takesArgument(1, Object.class)
                            .or(takesArgument(1, named("org.springframework.amqp.core.Message"))))),
        getClass().getName() + "$InvokeListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeListenerAdvice {

    public static ContextKey RABBIT_CHANNEL_CONTEXT_KEY = ContextKey.named("opentelemetry-rabbit-channel");

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Object channel,
        @Advice.Argument(1) Object data,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (!(channel instanceof Channel) && !(data instanceof Message)) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      Message message = (Message) data;
      if (instrumenter().shouldStart(parentContext, message)) {
        parentContext = parentContext.with(RABBIT_CHANNEL_CONTEXT_KEY, channel);
        scope = parentContext.makeCurrent();

        context = instrumenter().start(parentContext, message);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onEnter(
        @Advice.Argument(1) Object data,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null || !(data instanceof Message)) {
        return;
      }
      scope.close();
      instrumenter().end(context, (Message) data, null, throwable);
    }
  }
}
