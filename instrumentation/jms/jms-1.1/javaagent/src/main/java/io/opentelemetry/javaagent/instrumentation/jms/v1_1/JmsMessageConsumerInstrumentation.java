/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsReceiveSpanUtil.createReceiveSpan;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.MessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive")
            .and(takesArguments(0).or(takesArguments(1)))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("receiveNoWait")
            .and(takesArguments(0))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("setMessageListener")
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.jms.MessageListener")))
            .and(isPublic()),
        getClass().getName() + "$SetMessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.This MessageConsumer consumer,
        @Advice.Enter Timer timer,
        @Advice.Return @Nullable Message message) {
      if (message == null) {
        // Do not create span when no message is received
        return;
      }

      String subscriptionName = JmsSubscriptionNames.get(consumer);
      JmsSubscriptionNames.set(message, subscriptionName);
      MessageWithDestination request =
          MessageWithDestination.create(
              JavaxMessageAdapter.create(message), null, subscriptionName);

      createReceiveSpan(consumerReceiveInstrumenter(), request, timer, null);
    }
  }

  @SuppressWarnings("unused")
  public static class SetMessageListenerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static String onEnter(
        @Advice.This MessageConsumer consumer,
        @Advice.Argument(0) @Nullable MessageListener messageListener) {
      if (messageListener == null) {
        return null;
      }
      String previousSubscriptionName = JmsSubscriptionNames.get(messageListener);
      JmsSubscriptionNames.copyToListener(consumer, messageListener);
      return previousSubscriptionName;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) @Nullable MessageListener messageListener,
        @Advice.Enter @Nullable String previousSubscriptionName,
        @Advice.Thrown @Nullable Throwable throwable) {
      if (throwable != null && messageListener != null) {
        JmsSubscriptionNames.set(messageListener, previousSubscriptionName);
      }
    }
  }
}
