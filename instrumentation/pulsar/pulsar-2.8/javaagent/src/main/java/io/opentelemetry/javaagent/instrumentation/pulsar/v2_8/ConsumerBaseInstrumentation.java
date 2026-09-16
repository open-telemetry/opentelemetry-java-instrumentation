/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.MessageListenerContext.currentReceiveSpanSuppression;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ConsumerBaseInstrumentation implements TypeInstrumentation {

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
        named("triggerListener")
            .or(nameStartsWith("lambda$triggerListener$"))
            .and(takesArguments(0))
            .or(named("receiveMessageFromConsumer")),
        getClass().getName() + "$TriggerListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class TriggerListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Boolean onEnter() {
      return currentReceiveSpanSuppression().set(Boolean.TRUE);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Boolean previous) {
      currentReceiveSpanSuppression().restore(previous);
    }
  }
}
