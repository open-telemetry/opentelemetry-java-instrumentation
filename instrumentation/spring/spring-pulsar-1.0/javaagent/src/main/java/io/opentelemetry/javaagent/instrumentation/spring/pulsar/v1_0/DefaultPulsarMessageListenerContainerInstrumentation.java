/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0.SpringPulsarSingletons.consumerProcessInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.VirtualFieldStore;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Message;

class DefaultPulsarMessageListenerContainerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named(
        "org.springframework.pulsar.listener.DefaultPulsarMessageListenerContainer$Listener");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatchMessageToListener")
            .and(takesArguments(3).or(takesArguments(2)))
            .and(takesArgument(0, named("org.apache.pulsar.client.api.Message"))),
        getClass().getName() + "$DispatchMessageToListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchMessageToListenerAdvice {
    public static class AdviceScope {
      private final Message<?> request;
      private final Context currentContext;
      private final Scope currentScope;

      private AdviceScope(Message<?> request, Context currentContext, Scope currentScope) {
        this.request = request;
        this.currentContext = currentContext;
        this.currentScope = currentScope;
      }

      @Nullable
      public static AdviceScope start(Message<?> request) {
        Context parentContext = VirtualFieldStore.extractProcessParentContext(request);
        if (!consumerProcessInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context currentContext = consumerProcessInstrumenter().start(parentContext, request);
        return new AdviceScope(request, currentContext, currentContext.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        currentScope.close();
        consumerProcessInstrumenter().end(currentContext, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.Argument(0) Message<?> message) {
      return AdviceScope.start(message);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
