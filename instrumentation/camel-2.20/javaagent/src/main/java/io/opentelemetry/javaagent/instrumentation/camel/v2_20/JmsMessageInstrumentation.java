/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessageTelemetry.messageTelemetry;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.jms.Message;
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
        @Advice.This org.apache.camel.Message camelMessage,
        @Advice.Argument(0) Message jmsMessage) {
      // a Camel message is refilled when its JMS message is swapped, so what it carried before must
      // not survive
      MessagingTelemetryCarrier<Message> jmsMessageTelemetry =
          MessagingTelemetryCarrier.create(
              VirtualField.find(Message.class, MessagingTelemetryClaims.class));
      messageTelemetry().replaceFrom(jmsMessageTelemetry, jmsMessage, camelMessage);
    }
  }
}
