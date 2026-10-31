/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.JmsSendOperation;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageProducerInstrumentation implements TypeInstrumentation {

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
    ElementMatcher.Junction<MethodDescription> send = named("send").and(isPublic());

    transformer.applyAdviceToMethod(
        send.and(
            takesArguments("javax.jms.Message")
                .or(takesArguments("javax.jms.Message", "int", "int", "long"))),
        getClass().getName() + "$ProducerAdvice");
    transformer.applyAdviceToMethod(
        send.and(
            takesArguments("javax.jms.Destination", "javax.jms.Message")
                .or(
                    takesArguments(
                        "javax.jms.Destination", "javax.jms.Message", "int", "int", "long"))),
        getClass().getName() + "$ProducerWithDestinationAdvice");

    transformer.applyAdviceToMethod(
        send.and(takesArguments("javax.jms.Message", "javax.jms.CompletionListener")),
        getClass().getName() + "$AsyncProducerAdvice");
    transformer.applyAdviceToMethod(
        send.and(
            takesArguments(
                "javax.jms.Message", "int", "int", "long", "javax.jms.CompletionListener")),
        getClass().getName() + "$AsyncProducerWithOptionsAdvice");
    transformer.applyAdviceToMethod(
        send.and(
            takesArguments(
                "javax.jms.Destination", "javax.jms.Message", "javax.jms.CompletionListener")),
        getClass().getName() + "$AsyncProducerWithDestinationAdvice");
    transformer.applyAdviceToMethod(
        send.and(
            takesArguments(
                "javax.jms.Destination",
                "javax.jms.Message",
                "int",
                "int",
                "long",
                "javax.jms.CompletionListener")),
        getClass().getName() + "$AsyncProducerWithDestinationAndOptionsAdvice");
  }

  public static class AdviceScope {
    private final CallDepth callDepth;
    @Nullable private final JmsSendOperation operation;
    @Nullable private final Scope scope;

    private AdviceScope(
        CallDepth callDepth, @Nullable JmsSendOperation operation, @Nullable Scope scope) {
      this.callDepth = callDepth;
      this.operation = operation;
      this.scope = scope;
    }

    public static AdviceScope start(MessageProducer producer, Message message) {
      Destination destination;
      try {
        destination = producer.getDestination();
      } catch (JMSException ignored) {
        destination = null;
      }
      return start(CallDepth.forClass(MessageProducer.class), destination, message);
    }

    public static AdviceScope start(Destination destination, Message message) {
      return start(CallDepth.forClass(MessageProducer.class), destination, message);
    }

    private static AdviceScope start(
        CallDepth callDepth, @Nullable Destination destination, Message message) {
      if (callDepth.getAndIncrement() > 0) {
        return new AdviceScope(callDepth, null, null);
      }
      Context parentContext = Context.current();

      MessageWithDestination messageWithDestination =
          MessageWithDestination.create(
              JavaxMessageAdapter.create(message),
              destination == null ? null : JavaxDestinationAdapter.create(destination));
      if (!producerInstrumenter().shouldStart(parentContext, messageWithDestination)) {
        return new AdviceScope(callDepth, null, null);
      }

      Context context = producerInstrumenter().start(parentContext, messageWithDestination);
      return new AdviceScope(
          callDepth,
          new JmsSendOperation(
              producerInstrumenter(), messageWithDestination, parentContext, context),
          context.makeCurrent());
    }

    @Nullable
    public Object wrapListener(@Nullable Object listener) {
      return operation == null ? listener : operation.wrapListener(listener, MessageProducer.class);
    }

    public void end(@Nullable Throwable throwable) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope == null || operation == null) {
        return;
      }

      scope.close();
      operation.endOnReturn(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Message message, @Advice.This MessageProducer producer) {
      return AdviceScope.start(producer, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable AdviceScope scope) {
      if (scope != null) {
        scope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerWithDestinationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Destination destination, @Advice.Argument(1) Message message) {
      return AdviceScope.start(destination, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter @Nullable AdviceScope scope) {
      if (scope != null) {
        scope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncProducerAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Message message,
        @Advice.Argument(1) @Nullable Object listener,
        @Advice.This MessageProducer producer) {
      AdviceScope scope = AdviceScope.start(producer, message);
      return new Object[] {scope, scope.wrapListener(listener)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[0]).end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncProducerWithOptionsAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 4, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Message message,
        @Advice.Argument(4) @Nullable Object listener,
        @Advice.This MessageProducer producer) {
      AdviceScope scope = AdviceScope.start(producer, message);
      return new Object[] {scope, scope.wrapListener(listener)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[0]).end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncProducerWithDestinationAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.Argument(2) @Nullable Object listener) {
      AdviceScope scope = AdviceScope.start(destination, message);
      return new Object[] {scope, scope.wrapListener(listener)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[0]).end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AsyncProducerWithDestinationAndOptionsAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 5, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.Argument(5) @Nullable Object listener) {
      AdviceScope scope = AdviceScope.start(destination, message);
      return new Object[] {scope, scope.wrapListener(listener)};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[0]).end(throwable);
      }
    }
  }
}
