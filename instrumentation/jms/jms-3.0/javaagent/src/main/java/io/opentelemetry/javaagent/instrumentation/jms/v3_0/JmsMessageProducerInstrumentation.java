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
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import javax.annotation.Nullable;
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

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final MessageWithDestination messageWithDestination;
    @Nullable private final Context context;
    @Nullable private final Scope scope;

    private AdviceScope(
        CallDepth callDepth,
        @Nullable MessageWithDestination messageWithDestination,
        @Nullable Context context,
        @Nullable Scope scope) {
      this.callDepth = callDepth;
      this.messageWithDestination = messageWithDestination;
      this.context = context;
      this.scope = scope;
    }

    public static AdviceScope start(CallDepth callDepth, Destination destination, Message message) {
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(callDepth, null, null, null);
      }
      Context parentContext = Context.current();

      MessageWithDestination messageWithDestination =
          MessageWithDestination.create(
              JakartaMessageAdapter.create(message), JakartaDestinationAdapter.create(destination));
      if (!producerInstrumenter().shouldStart(parentContext, messageWithDestination)) {
        return new AdviceScope(callDepth, null, null, null);
      }

      Context context = producerInstrumenter().start(parentContext, messageWithDestination);
      return new AdviceScope(callDepth, messageWithDestination, context, context.makeCurrent());
    }

    public void end(@Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope == null || context == null || messageWithDestination == null) {
        return;
      }

      scope.close();
      producerInstrumenter().end(context, messageWithDestination, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Message message, @Advice.This MessageProducer producer) {
      Destination destination;
      try {
        destination = producer.getDestination();
      } catch (JMSException e) {
        destination = null;
      }
      CallDepth callDepth = CallDepth.forClass(MessageProducer.class);
      return AdviceScope.start(callDepth, destination, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Destination destination, @Advice.Argument(1) Message message) {
      CallDepth callDepth = CallDepth.forClass(MessageProducer.class);
      return AdviceScope.start(callDepth, destination, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
