/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0.SpringRabbitSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.amqp.core.Message;

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
      private final Message message;

      public AdviceScope(Context context, Message message) {
        this.context = context;
        this.scope = context.makeCurrent();
        this.message = message;
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, message, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.Argument(1) Object data) {
      if (!(data instanceof Message)) {
        return null;
      }
      Context parentContext = Java8BytecodeBridge.currentContext();
      Message message = (Message) data;
      if (!instrumenter().shouldStart(parentContext, message)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, message);
      return new AdviceScope(context, message);
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
