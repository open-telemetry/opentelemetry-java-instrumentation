/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsSessionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("jakarta.jms.Session");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("jakarta.jms.Session"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf(
                "createDurableSubscriber",
                "createDurableConsumer",
                "createSharedConsumer",
                "createSharedDurableConsumer")
            .and(takesArgument(1, String.class))
            .and(isPublic()),
        getClass().getName() + "$CreateDurableConsumerAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf(
                "createConsumer",
                "createReceiver",
                "createSubscriber",
                "createDurableSubscriber",
                "createDurableConsumer",
                "createSharedConsumer",
                "createSharedDurableConsumer")
            .and(isPublic()),
        getClass().getName() + "$CreateConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("close").and(takesArguments(0)).and(isPublic()),
        getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateDurableConsumerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) String subscriptionName,
        @Advice.Return @Nullable MessageConsumer consumer) {
      if (consumer != null) {
        JmsSubscriptionNames.set(consumer, subscriptionName);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CreateConsumerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Session session, @Advice.Return @Nullable MessageConsumer consumer) {
      if (consumer != null) {
        JmsSubscriptionNames.setSession(consumer, session);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Session session) {
      JmsSubscriptionNames.clearSession(session);
    }
  }
}
