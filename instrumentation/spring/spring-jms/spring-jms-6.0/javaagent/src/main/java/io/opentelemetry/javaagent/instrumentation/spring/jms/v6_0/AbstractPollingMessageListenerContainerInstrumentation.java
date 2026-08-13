/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0.SpringJmsSingletons.RECEIVE_TELEMETRY_ENABLED;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.InternalListenerPollContext;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContextHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AbstractPollingMessageListenerContainerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.jms.listener.AbstractPollingMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receiveAndExecute"), getClass().getName() + "$ReceiveAndExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAndExecuteAdvice {

    public static class AdviceScope {
      private final boolean previouslyActive;
      @Nullable private final Scope scope;

      private AdviceScope(boolean previouslyActive, @Nullable Scope scope) {
        this.previouslyActive = previouslyActive;
        this.scope = scope;
      }

      public static AdviceScope enter() {
        // mark this receive as an internal listener poll so that the underlying JMS receive
        // instrumentation can tell it apart from an application-initiated pull
        boolean previouslyActive = InternalListenerPollContext.enter();
        Scope scope = null;
        if (RECEIVE_TELEMETRY_ENABLED) {
          Context context = JmsReceiveContextHolder.init(Java8BytecodeBridge.currentContext());
          scope = context.makeCurrent();
        }
        return new AdviceScope(previouslyActive, scope);
      }

      public void exit() {
        if (scope != null) {
          scope.close();
        }
        InternalListenerPollContext.exit(previouslyActive);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter() {
      return AdviceScope.enter();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter AdviceScope adviceScope) {
      adviceScope.exit();
    }
  }
}
