/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.consumerProcessInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import javax.annotation.Nullable;
import javax.jms.Message;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JmsMessageListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.MessageListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageListener"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        getClass().getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    public static class AdviceScope {
      private final Message message;
      private final boolean clearSubscriptionName;
      private final MessageWithDestination messageWithDestination;
      @Nullable private final Context context;
      @Nullable private final Scope scope;

      private AdviceScope(
          Message message,
          boolean clearSubscriptionName,
          MessageWithDestination messageWithDestination,
          @Nullable Context context,
          @Nullable Scope scope) {
        this.message = message;
        this.clearSubscriptionName = clearSubscriptionName;
        this.messageWithDestination = messageWithDestination;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(MessageListener messageListener, Message message) {
        Context parentContext = Context.current();
        String subscriptionName = JmsSubscriptionNames.get(message);
        boolean clearSubscriptionName = false;
        if (subscriptionName == null) {
          subscriptionName = JmsSubscriptionNames.get(messageListener);
          if (subscriptionName != null) {
            clearSubscriptionName = true;
            JmsSubscriptionNames.set(message, subscriptionName);
          }
        }
        try {
          MessageWithDestination messageWithDestination =
              MessageWithDestination.create(
                  JavaxMessageAdapter.create(message), null, subscriptionName);

          AdviceScope adviceScope;
          if (!consumerProcessInstrumenter().shouldStart(parentContext, messageWithDestination)) {
            adviceScope =
                new AdviceScope(message, clearSubscriptionName, messageWithDestination, null, null);
          } else {
            Context context =
                consumerProcessInstrumenter().start(parentContext, messageWithDestination);
            adviceScope =
                new AdviceScope(
                    message,
                    clearSubscriptionName,
                    messageWithDestination,
                    context,
                    context.makeCurrent());
          }
          clearSubscriptionName = false;
          return adviceScope;
        } finally {
          if (clearSubscriptionName) {
            JmsSubscriptionNames.set(message, null);
          }
        }
      }

      public void end(@Nullable Throwable throwable) {
        try {
          if (context != null && scope != null) {
            scope.close();
            consumerProcessInstrumenter().end(context, messageWithDestination, null, throwable);
          }
        } finally {
          if (clearSubscriptionName) {
            JmsSubscriptionNames.set(message, null);
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
