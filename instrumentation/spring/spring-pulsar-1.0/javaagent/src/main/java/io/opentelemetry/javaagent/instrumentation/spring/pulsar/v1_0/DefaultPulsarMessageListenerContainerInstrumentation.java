/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0.SpringPulsarSingletons.instrumenter;
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

public class DefaultPulsarMessageListenerContainerInstrumentation implements TypeInstrumentation {
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
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      public void exit(@Nullable Throwable throwable, Message<?> message) {
        scope.close();
        instrumenter().end(context, message, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Message<?> message) {
      Context parentContext = VirtualFieldStore.extract(message);
      if (!instrumenter().shouldStart(parentContext, message)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, message);
      return new AdviceScope(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Message<?> message,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(throwable, message);
      }
    }
  }
}
