/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSubscriptionNames;
import javax.annotation.Nullable;
import javax.jms.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

class SpringJmsSubscriptionNameInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.jms.listener.AbstractMessageListenerContainer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("setSubscriptionName").and(takesArguments(1)).and(takesArgument(0, String.class)),
        getClass().getName() + "$SetSubscriptionNameAdvice");
    transformer.applyAdviceToMethod(
        named("setDurableSubscriptionName")
            .and(takesArguments(1))
            .and(takesArgument(0, String.class)),
        getClass().getName() + "$SetDurableSubscriptionNameAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("getSubscriptionName", "getDurableSubscriptionName")
            .and(takesArguments(0))
            .and(returns(String.class)),
        getClass().getName() + "$GetSubscriptionNameAdvice");
    transformer.applyAdviceToMethod(
        named("setSubscriptionDurable").and(takesArguments(1)).and(takesArgument(0, boolean.class)),
        getClass().getName() + "$SetSubscriptionDurableAdvice");
    transformer.applyAdviceToMethod(
        named("setSubscriptionShared").and(takesArguments(1)).and(takesArgument(0, boolean.class)),
        getClass().getName() + "$SetSubscriptionSharedAdvice");
    transformer.applyAdviceToMethod(
        named("invokeListener")
            .and(takesArguments(2))
            .and(takesArgument(1, named("javax.jms.Message"))),
        getClass().getName() + "$InvokeListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetSubscriptionNameAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(0) @Nullable String subscriptionName) {
      SpringJmsSubscriptionNames.set(container, subscriptionName);
    }
  }

  @SuppressWarnings("unused")
  public static class SetDurableSubscriptionNameAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(0) @Nullable String subscriptionName) {
      SpringJmsSubscriptionNames.set(container, subscriptionName);
      SpringJmsSubscriptionNames.setDurable(container, true);
    }
  }

  @SuppressWarnings("unused")
  public static class GetSubscriptionNameAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Return @Nullable String subscriptionName) {
      SpringJmsSubscriptionNames.set(container, subscriptionName);
    }
  }

  @SuppressWarnings("unused")
  public static class SetSubscriptionDurableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(0) boolean subscriptionDurable) {
      SpringJmsSubscriptionNames.setDurable(container, subscriptionDurable);
    }
  }

  @SuppressWarnings("unused")
  public static class SetSubscriptionSharedAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(0) boolean subscriptionShared) {
      SpringJmsSubscriptionNames.setShared(container, subscriptionShared);
    }
  }

  @SuppressWarnings("unused")
  public static class InvokeListenerAdvice {

    // the container's subscription name applies to this dispatch only; the previous name is
    // restored when the dispatch returns, so that a later dispatch of the same message doesn't
    // report this container's subscription
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static String onEnter(
        @Advice.This AbstractMessageListenerContainer container,
        @Advice.Argument(1) Message message) {
      String previousSubscriptionName = JmsSubscriptionNames.get(message);
      SpringJmsSubscriptionNames.set(message, container);
      return previousSubscriptionName;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) Message message,
        @Advice.Enter @Nullable String previousSubscriptionName) {
      JmsSubscriptionNames.set(message, previousSubscriptionName);
    }
  }
}
