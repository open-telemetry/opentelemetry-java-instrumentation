/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.spring.integration.v4_1.internal.SpringIntegrationHandoff;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.messaging.Message;

class MessageHistoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.integration.history.MessageHistory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("write")
            .and(takesArgument(0, named("org.springframework.messaging.Message")))
            .and(returns(named("org.springframework.messaging.Message"))),
        getClass().getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) Message<?> inputMessage, @Advice.Return Message<?> outputMessage) {
      SpringIntegrationHandoff.forward(inputMessage, outputMessage);
    }
  }
}
