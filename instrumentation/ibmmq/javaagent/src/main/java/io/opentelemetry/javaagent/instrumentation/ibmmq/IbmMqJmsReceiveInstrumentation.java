/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import javax.jms.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Captures the queue manager identifier when a message is returned from a synchronous {@code
 * receive()}/{@code receiveNoWait()} call on an IBM MQ consumer, and remembers it against that
 * exact {@link Message} via {@link IbmMqJmsListenerQmid#captureFromReceive}.
 *
 * <p>This never touches the receive span itself: the generic JMS instrumentation creates that span
 * via {@code startAndEnd} in its own exit advice, ended in the same call and never made current, so
 * no advice anywhere can enrich it -- see the note in {@link IbmMqInstrumentationModule}. What this
 * advice does is unrelated: it stashes a plain value on the returned message so that whichever
 * <em>later</em>, already-writable span ends up processing it can pick the QMID up -- most commonly
 * the {@code onMessage} process span, for containers (such as Spring's default {@code
 * JmsListenerContainerFactory}) that drive their listener by calling {@code receive()} and invoking
 * it directly, without ever calling {@code setMessageListener}. See {@link IbmMqJmsListenerQmid}.
 *
 * <p>Both IBM's internal consumer implementation and its public facade class implement {@code
 * JmsMessageConsumer} and both declare all three receive methods, so this advice runs twice,
 * nested, per application-level {@code receive()} call. Harmless: both reads come from the same
 * connection and produce the same QMID, and the {@code VirtualField} write is idempotent.
 */
public class IbmMqJmsReceiveInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.ibm.msg.client.jms.JmsMessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.ibm.msg.client.jms.JmsMessageConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive")
            .or(named("receiveNoWait"))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        this.getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This Object consumer, @Advice.Return @Nullable Message message) {
      IbmMqJmsListenerQmid.captureFromReceive(consumer, message);
    }
  }
}
