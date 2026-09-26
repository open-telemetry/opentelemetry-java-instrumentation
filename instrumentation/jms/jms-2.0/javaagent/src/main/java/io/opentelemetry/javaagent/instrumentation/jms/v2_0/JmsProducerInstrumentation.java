/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.v2_0.JmsSingletons.producerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import io.opentelemetry.javaagent.instrumentation.jms.v1_1.JavaxDestinationAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.v1_1.JavaxMessageAdapter;
import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instruments the simplified API's {@code JMSProducer}, introduced in JMS 2.0.
 *
 * <p>Only the {@code send(Destination, Message)} overload is instrumented. The body-convenience
 * overloads ({@code String}, {@code Map}, {@code byte[]}, {@code Serializable}) build the {@code
 * Message} inside the provider, so there is no message at this boundary to inject trace context
 * into.
 */
class JmsProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.JMSProducer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.JMSProducer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("javax.jms.Destination")))
            .and(takesArgument(1, named("javax.jms.Message")))
            .and(isPublic()),
        getClass().getName() + "$ProducerAdvice");
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
              JavaxMessageAdapter.create(message), JavaxDestinationAdapter.create(destination));
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

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.Argument(0) Destination destination, @Advice.Argument(1) Message message) {
      // deliberately keyed on MessageProducer, not JMSProducer: providers typically implement
      // JMSProducer.send by delegating to a MessageProducer, and sharing the key means the
      // outermost send wins and exactly one producer span is emitted
      CallDepth callDepth = CallDepth.forClass(MessageProducer.class);
      return AdviceScope.start(callDepth, destination, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
