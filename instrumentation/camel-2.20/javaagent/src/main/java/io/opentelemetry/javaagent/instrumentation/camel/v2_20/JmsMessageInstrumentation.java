/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveTelemetry.copy;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.camel.component.jms.JmsMessage");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("javax.jms.Message"))),
        getClass().getName() + "$StoreReceiveTelemetryAdvice");
    transformer.applyAdviceToMethod(
        named("setJmsMessage").and(takesArgument(0, named("javax.jms.Message"))),
        getClass().getName() + "$StoreReceiveTelemetryAdvice");
  }

  @SuppressWarnings("unused")
  public static class StoreReceiveTelemetryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object camelMessage, @Advice.Argument(0) Object jmsMessage) {
      copy(jmsMessage, camelMessage);
    }
  }
}
