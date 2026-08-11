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
 * Adds the IBM MQ queue manager identifier to the consumer span created by the generic JMS
 * instrumentation.
 */
public class IbmMqJmsConsumerInstrumentation implements TypeInstrumentation {

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
        named("receive").or(named("receiveNoWait")).and(isPublic()),
        this.getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    // The receive span is opened by the generic JMS instrumentation once a message has arrived, so
    // exit is the likely landing point; enter is kept as a harmless no-op fallback.
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This Object consumer) {
      IbmMqJmsQmid.stamp(consumer);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This Object consumer) {
      IbmMqJmsQmid.stamp(consumer);
    }
  }
}
