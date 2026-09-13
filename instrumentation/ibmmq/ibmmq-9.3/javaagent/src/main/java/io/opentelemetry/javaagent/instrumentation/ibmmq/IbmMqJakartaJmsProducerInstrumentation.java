/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IbmMqJakartaJmsProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.ibm.msg.client.jakarta.jms.JmsMessageProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.ibm.msg.client.jakarta.jms.JmsMessageProducer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send").and(isPublic()), this.getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    // This module's order() installs it after the generic JMS instrumentation, whose entry advice
    // has therefore already opened the producer span and made it current by the time this runs.
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Object producer) {
      IbmMqJakartaJmsQmid.stampMessagingSpan(producer);
    }
  }
}
