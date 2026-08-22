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

/**
 * Adds the IBM MQ queue manager identifier to the producer span created by the generic JMS
 * instrumentation.
 *
 * <p>Matching IBM's own {@code com.ibm.msg.client.jms.JmsMessageProducer} interface rather than
 * {@code com.ibm.msg.client.wmq.internal} keeps this on IBM's supported client API surface.
 */
public class IbmMqJmsProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.ibm.msg.client.jms.JmsMessageProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.ibm.msg.client.jms.JmsMessageProducer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send").and(isPublic()), this.getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    // Stamped on both enter and exit so that the attribute lands regardless of how this module's
    // advice is ordered relative to the generic JMS instrumentation that opens the span. Both are
    // no-ops when no span is recording, and setAttribute is idempotent.
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Object producer) {
      IbmMqJmsQmid.stamp(producer);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This Object producer) {
      IbmMqJmsQmid.stamp(producer);
    }
  }
}
