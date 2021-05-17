/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.jms.JmsInstrumenters.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JmsMessageProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.MessageProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageProducer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        JmsMessageProducerInstrumentation.class.getName() + "$ProducerAdvice");
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        JmsMessageProducerInstrumentation.class.getName() + "$ProducerWithDestinationAdvice");
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Message message,
        @Advice.This MessageProducer producer,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return;
      }

      Destination defaultDestination;
      try {
        defaultDestination = producer.getDestination();
      } catch (JMSException e) {
        defaultDestination = null;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = MessageWithDestination.create(message, MessageOperation.SEND, defaultDestination);
      if (!producerInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = producerInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(MessageProducer.class);

      scope.close();
      producerInstrumenter().end(context, request, null, throwable);
    }
  }

  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(MessageProducer.class);
      if (callDepth > 0) {
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = MessageWithDestination.create(message, MessageOperation.SEND, destination);
      if (!producerInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = producerInstrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(MessageProducer.class);

      scope.close();
      producerInstrumenter().end(context, request, null, throwable);
    }
  }
}
