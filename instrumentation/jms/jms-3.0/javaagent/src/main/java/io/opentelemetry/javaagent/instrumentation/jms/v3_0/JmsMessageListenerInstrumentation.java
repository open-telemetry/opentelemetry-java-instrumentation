/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.v3_0.JmsSingletons.consumerProcessInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("jakarta.jms.MessageListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("jakarta.jms.MessageListener"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage").and(takesArgument(0, named("jakarta.jms.Message"))).and(isPublic()),
        getClass().getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    public static class AdviceScope {
      private final Instrumenter<MessageWithDestination, Void> instrumenter;
      private final MessageWithDestination messageWithDestination;
      @Nullable private final Context context;
      @Nullable private final Scope scope;
      // the message that this callback attached the listener's subscription name to, and that has
      // to be cleared again when the callback returns
      @Nullable private final Message messageWithListenerSubscriptionName;

      private AdviceScope(
          Instrumenter<MessageWithDestination, Void> instrumenter,
          MessageWithDestination messageWithDestination,
          @Nullable Context context,
          @Nullable Scope scope,
          @Nullable Message messageWithListenerSubscriptionName) {
        this.instrumenter = instrumenter;
        this.messageWithDestination = messageWithDestination;
        this.context = context;
        this.scope = scope;
        this.messageWithListenerSubscriptionName = messageWithListenerSubscriptionName;
      }

      public static AdviceScope start(MessageListener messageListener, Message message) {
        Message messageWithListenerSubscriptionName =
            attachListenerSubscriptionName(messageListener, message);
        MessageWithDestination messageWithDestination =
            MessageWithDestination.create(
                JakartaMessageAdapter.create(message), null, JmsSubscriptionNames.get(message));

        Context parentContext = Context.current();
        Instrumenter<MessageWithDestination, Void> instrumenter =
            consumerProcessInstrumenter(
                messageWithDestination.message().wasReceiveTelemetryRecorded());
        if (!instrumenter.shouldStart(parentContext, messageWithDestination)) {
          // an advice scope is still needed, to clear the listener's subscription name on exit
          return new AdviceScope(
              instrumenter,
              messageWithDestination,
              null,
              null,
              messageWithListenerSubscriptionName);
        }

        Context context = instrumenter.start(parentContext, messageWithDestination);
        return new AdviceScope(
            instrumenter,
            messageWithDestination,
            context,
            context.makeCurrent(),
            messageWithListenerSubscriptionName);
      }

      // a name that a synchronous receive or a Spring dispatch attached to the message wins over
      // the listener's name; otherwise the listener's name is attached to the message for the
      // duration of this callback, so that a nested dispatch of the same message sees it
      //
      // returns the message when its subscription name has to be cleared when the callback returns
      @Nullable
      private static Message attachListenerSubscriptionName(
          MessageListener messageListener, Message message) {
        if (JmsSubscriptionNames.get(message) != null) {
          return null;
        }
        String subscriptionName = JmsSubscriptionNames.get(messageListener);
        if (subscriptionName == null) {
          return null;
        }
        JmsSubscriptionNames.set(message, subscriptionName);
        return message;
      }

      public void end(@Nullable Throwable throwable) {
        try {
          if (context != null && scope != null) {
            scope.close();
            instrumenter.end(context, messageWithDestination, null, throwable);
          }
        } finally {
          if (messageWithListenerSubscriptionName != null) {
            JmsSubscriptionNames.set(messageWithListenerSubscriptionName, null);
          }
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This MessageListener messageListener, @Advice.Argument(0) Message message) {
      return AdviceScope.start(messageListener, message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
