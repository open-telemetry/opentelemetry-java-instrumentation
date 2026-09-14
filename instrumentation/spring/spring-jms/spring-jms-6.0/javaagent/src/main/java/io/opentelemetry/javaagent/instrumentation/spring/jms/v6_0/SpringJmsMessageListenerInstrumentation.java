/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0.SpringJmsSingletons.listenerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.common.v1_1.MessageWithDestination;
import io.opentelemetry.javaagent.instrumentation.jms.v3_0.JakartaMessageAdapter;
import io.opentelemetry.javaagent.instrumentation.jms.v3_0.JmsSubscriptionNames;
import jakarta.jms.Message;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class SpringJmsMessageListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.jms.listener.SessionAwareMessageListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(
        named("org.springframework.jms.listener.SessionAwareMessageListener"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage")
            .and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, named("jakarta.jms.Message"))),
        getClass().getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    public static class AdviceScope {
      private final Instrumenter<MessageWithDestination, Void> instrumenter;
      private final MessageWithDestination request;
      private final MessageAdapter messageAdapter;
      @Nullable private final Context context;
      @Nullable private final Scope scope;

      private AdviceScope(
          Instrumenter<MessageWithDestination, Void> instrumenter,
          MessageWithDestination request,
          MessageAdapter messageAdapter,
          @Nullable Context context,
          @Nullable Scope scope) {
        this.instrumenter = instrumenter;
        this.request = request;
        this.messageAdapter = messageAdapter;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Message message) {
        MessageAdapter messageAdapter = JakartaMessageAdapter.create(message);
        MessageWithDestination request =
            MessageWithDestination.create(messageAdapter, null, JmsSubscriptionNames.get(message));
        messageAdapter.beginProcessing();

        Context currentContext = Context.current();
        if (!listenerInstrumenter(true).shouldStart(currentContext, request)) {
          return new AdviceScope(listenerInstrumenter(true), request, messageAdapter, null, null);
        }

        Context parentContext = currentContext;
        if (!emitStableMessagingSemconv()) {
          JmsReceiveContext receiveContext = messageAdapter.getReceiveContext();
          if (receiveContext != null) {
            parentContext = receiveContext.context();
          }
        }
        Instrumenter<MessageWithDestination, Void> instrumenter =
            listenerInstrumenter(!messageAdapter.claimConsumedMessages());
        if (!instrumenter.shouldStart(parentContext, request)) {
          return new AdviceScope(instrumenter, request, messageAdapter, null, null);
        }

        Context context = instrumenter.start(parentContext, request);
        return new AdviceScope(
            instrumenter, request, messageAdapter, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        try {
          if (context != null && scope != null) {
            scope.close();
            instrumenter.end(context, request, null, throwable);
          }
        } finally {
          messageAdapter.endProcessing();
        }
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(@Advice.Argument(0) Message message) {
      return AdviceScope.start(message);
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
