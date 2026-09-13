/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.jms.JmsMessageDeliveryState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.jms.Message;
import javax.annotation.Nullable;
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

    private static final VirtualField<Message, JmsMessageDeliveryState> JMS_DELIVERY_STATE =
        VirtualField.find(Message.class, JmsMessageDeliveryState.class);
    private static final VirtualField<org.apache.camel.Message, JmsMessageDeliveryState>
        CAMEL_DELIVERY_STATE =
            VirtualField.find(org.apache.camel.Message.class, JmsMessageDeliveryState.class);

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This org.apache.camel.Message camelMessage,
        @Advice.Argument(0) @Nullable Message jmsMessage) {
      if (jmsMessage == null) {
        CAMEL_DELIVERY_STATE.set(camelMessage, null);
        return;
      }
      JmsMessageDeliveryState state = JMS_DELIVERY_STATE.get(jmsMessage);
      if (state == null) {
        synchronized (jmsMessage) {
          state = JMS_DELIVERY_STATE.get(jmsMessage);
          if (state == null) {
            state = new JmsMessageDeliveryState();
            JMS_DELIVERY_STATE.set(jmsMessage, state);
          }
        }
      }
      // A Camel message is refilled when its JMS message is swapped. Replace the delivery state,
      // without copying the receive context or retaining the previous message's accounting.
      CAMEL_DELIVERY_STATE.set(camelMessage, state);
    }
  }
}
