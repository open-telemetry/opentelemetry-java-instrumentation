/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

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
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
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
    transformer.applyAdviceToMethod(
        named("send").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        getClass().getName() + "$ProducerAdvice");
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ProducerWithDestinationAdvice");
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

    public static AdviceScope start(
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

    public Object[] wrapListener(Object[] arguments) {
      return operation == null
          ? arguments
          : operation.wrapListener(arguments, MessageProducer.class);
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

    @Advice.AssignReturned.ToAllArguments(index = 0, typing = Assigner.Typing.DYNAMIC)
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Message message,
        @Advice.This MessageProducer producer,
        @Advice.AllArguments Object[] arguments) {
      Destination destination;
      try {
        destination = producer.getDestination();
      } catch (JMSException ignored) {
        destination = null;
      }
      CallDepth callDepth = CallDepth.forClass(MessageProducer.class);
      AdviceScope adviceScope = AdviceScope.start(callDepth, destination, message);
      if (arguments.length == 2 || arguments.length == 5) {
        arguments = adviceScope.wrapListener(arguments);
      }
      return new Object[] {arguments, adviceScope};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[1]).end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProducerWithDestinationAdvice {

    @Advice.AssignReturned.ToAllArguments(index = 0, typing = Assigner.Typing.DYNAMIC)
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Argument(0) Destination destination,
        @Advice.Argument(1) Message message,
        @Advice.AllArguments Object[] arguments) {
      CallDepth callDepth = CallDepth.forClass(MessageProducer.class);
      AdviceScope adviceScope = AdviceScope.start(callDepth, destination, message);
      if (arguments.length == 3 || arguments.length == 6) {
        arguments = adviceScope.wrapListener(arguments);
      }
      return new Object[] {arguments, adviceScope};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult != null) {
        ((AdviceScope) enterResult[1]).end(throwable);
      }
    }
  }
}
