/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.javaagent.instrumentation.jms.JMSTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class JMSMessageConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.jms.MessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageConsumer"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic()),
        JMSMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    transformers.put(
        named("receiveNoWait").and(takesArguments(0)).and(isPublic()),
        JMSMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    return transformers;
  }

  public static class ConsumerAdvice {

    @Advice.OnMethodEnter
    public static long onEnter() {
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This MessageConsumer consumer,
        @Advice.Enter long startTime,
        @Advice.Return Message message,
        @Advice.Thrown Throwable throwable) {
      MessageDestination destination;
      if (message == null) {
        destination =
            InstrumentationContext.get(MessageConsumer.class, MessageDestination.class)
                .get(consumer);
        if (destination == null) {
          destination = MessageDestination.UNKNOWN;
        }
      } else {
        destination = tracer().extractDestination(message, null);
      }

      Span span = tracer().startConsumerSpan(destination, "receive", message, startTime);

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span);
      }
    }
  }
}
