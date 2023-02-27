/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.v3_0.JmsSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JmsMessageProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("jakarta.jms.MessageProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("jakarta.jms.MessageProducer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send").and(takesArgument(0, named("jakarta.jms.Message"))).and(isPublic()),
        JmsMessageProducerInstrumentation.class.getName() + "$ProducerAdvice");
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("jakarta.jms.Destination")))
            .and(takesArgument(1, named("jakarta.jms.Message")))
            .and(isPublic()),
        JmsMessageProducerInstrumentation.class.getName() + "$ProducerWithDestinationAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Message message,
        @Advice.This MessageProducer producer,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(MessageProducer.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Destination defaultDestination;
      try {
        defaultDestination = producer.getDestination();
      } catch (JMSException e) {
        defaultDestination = null;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      request =
          MessageWithDestination.create(
              JakartaMessageAdapter.create(message),
              JakartaDestinationAdapter.create(defaultDestination));
      if (!producerInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = producerInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        producerInstrumenter().end(context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(MessageProducer.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      request =
          MessageWithDestination.create(
              JakartaMessageAdapter.create(message), JakartaDestinationAdapter.create(destination));
      if (!producerInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = producerInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      if (scope != null) {
        scope.close();
        producerInstrumenter().end(context, request, null, throwable);
      }
    }
  }
}
