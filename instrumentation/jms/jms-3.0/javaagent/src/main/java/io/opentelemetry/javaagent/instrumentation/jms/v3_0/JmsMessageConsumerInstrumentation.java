/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsReceiveSpanUtil.createReceiveSpan;
import static io.opentelemetry.javaagent.instrumentation.jms.v3_0.JmsSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("jakarta.jms.MessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("jakarta.jms.MessageConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive")
            .and(takesArguments(0).or(takesArguments(1)))
            .and(returns(named("jakarta.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("receiveNoWait")
            .and(takesArguments(0))
            .and(returns(named("jakarta.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("setMessageListener")
            .and(takesArguments(1))
            .and(takesArgument(0, named("jakarta.jms.MessageListener")))
            .and(isPublic()),
        getClass().getName() + "$SetMessageListenerAdvice");
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final Timer timer;

    private AdviceScope(CallDepth callDepth, @Nullable Timer timer) {
      this.callDepth = callDepth;
      this.timer = timer;
    }

    public static AdviceScope enter() {
      CallDepth callDepth = CallDepth.forClass(MessageConsumer.class);
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(callDepth, null);
      }
      return new AdviceScope(callDepth, Timer.start());
    }

    public void exit(MessageConsumer consumer, @Nullable Message message) {
      if (callDepth.decrementAndGet() > 0 || timer == null) {
        return;
      }
      if (message == null) {
        // Do not create span when no message is received
        return;
      }

      String subscriptionName = JmsSubscriptionNames.get(consumer);
      JmsSubscriptionNames.set(message, subscriptionName);
      MessageWithDestination request =
          MessageWithDestination.create(
              JakartaMessageAdapter.create(message), null, subscriptionName);

      createReceiveSpan(consumerReceiveInstrumenter(), request, timer, null);
    }
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter() {
      return AdviceScope.enter();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.This MessageConsumer consumer,
        @Advice.Enter AdviceScope adviceScope,
        @Advice.Return @Nullable Message message) {
      adviceScope.exit(consumer, message);
    }
  }

  @SuppressWarnings("unused")
  public static class SetMessageListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This MessageConsumer consumer,
        @Advice.Argument(0) @Nullable MessageListener messageListener) {
      JmsSubscriptionNames.copyToListener(consumer, messageListener);
    }
  }
}
