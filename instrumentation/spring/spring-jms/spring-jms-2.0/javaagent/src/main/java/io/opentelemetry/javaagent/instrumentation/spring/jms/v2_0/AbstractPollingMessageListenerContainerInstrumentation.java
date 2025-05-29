/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0.SpringJmsSingletons.isReceiveTelemetryEnabled;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContextHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractPollingMessageListenerContainerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.jms.listener.AbstractPollingMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receiveAndExecute"), this.getClass().getName() + "$ReceiveAndExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAndExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      if (isReceiveTelemetryEnabled()) {
        Context context = JmsReceiveContextHolder.init(Java8BytecodeBridge.currentContext());
        return context.makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
    }
  }
}
