/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0.SpringRabbitSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.rabbitmq.client.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;

class AbstractMessageListenerContainerInstrumentation implements TypeInstrumentation {
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

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final SpringRabbitRequest request;

      @Nullable
      public static AdviceScope start(
          AbstractMessageListenerContainer container, Channel channel, Object data) {
        if (!SpringRabbitListenerUtil.isListenerProcessingSelected(container)) {
          return null;
        }

        SpringRabbitRequest request;
        if (data instanceof Message) {
          request = new SpringRabbitRequest(channel, (Message) data);
        } else if (data instanceof List
            && !((List<?>) data).isEmpty()
            && ((List<?>) data).get(0) instanceof Message) {
          List<Message> messages = new ArrayList<>();
          for (Object message : (List<?>) data) {
            if (!(message instanceof Message)) {
              return null;
            }
            messages.add((Message) message);
          }
          request = new SpringRabbitRequest(channel, messages);
        } else {
          return null;
        }

        Context parentContext = Context.current();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context;
        try (Scope ignored = Context.root().makeCurrent()) {
          context = instrumenter().start(parentContext, request);
        }
        request.installProcessingContext(context);
        try {
          return new AdviceScope(context, request);
        } catch (RuntimeException | Error e) {
          request.restoreProcessingContext(context);
          throw e;
        }
      }

      private AdviceScope(Context context, SpringRabbitRequest request) {
        this.context = context;
        this.request = request;
        this.scope = context.makeCurrent();
      }

      public void end(@Nullable Throwable throwable) {
        try {
          scope.close();
        } finally {
          request.restoreProcessingContext(context);
        }
        instrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(0) Channel channel,
        @Advice.Argument(1) Object data) {
      return AdviceScope.start(container, channel, data);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope == null) {
        return;
      }
      adviceScope.end(throwable);
    }
  }
}
