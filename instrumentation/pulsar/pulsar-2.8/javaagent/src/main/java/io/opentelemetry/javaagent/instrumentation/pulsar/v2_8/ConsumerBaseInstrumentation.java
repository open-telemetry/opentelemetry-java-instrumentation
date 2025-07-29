/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.MessageListenerContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ConsumerBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.pulsar.client.impl.ConsumerBase",
        "org.apache.pulsar.client.impl.MultiTopicsConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // these methods receive a message and pass it on to a message listener
    // we instrument them so that the span for the receive operation could be suppressed
    transformer.applyAdviceToMethod(
        named("triggerListener").and(takesArguments(0)).or(named("receiveMessageFromConsumer")),
        this.getClass().getName() + "$TriggerListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class TriggerListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      MessageListenerContext.startProcessing();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      MessageListenerContext.endProcessing();
    }
  }
}
