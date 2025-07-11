/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.jms.v6_0.SpringJmsSingletons.listenerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.jms.JmsReceiveContextHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;
import io.opentelemetry.javaagent.instrumentation.jms.v3_0.JakartaMessageAdapter;
import jakarta.jms.Message;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SpringJmsMessageListenerInstrumentation implements TypeInstrumentation {

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
        SpringJmsMessageListenerInstrumentation.class.getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    public static class AdviceScope {
      private final MessageWithDestination request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(MessageWithDestination request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope enter(Message message) {
        Context parentContext = Context.current();
        Context receiveContext = JmsReceiveContextHolder.getReceiveContext(parentContext);
        if (receiveContext != null) {
          parentContext = receiveContext;
        }
        MessageWithDestination request =
            MessageWithDestination.create(JakartaMessageAdapter.create(message), null);

        if (!listenerInstrumenter().shouldStart(parentContext, request)) {
          return null;
        }

        Context context = listenerInstrumenter().start(parentContext, request);
        return new AdviceScope(request, context, context.makeCurrent());
      }

      public void exit(Throwable throwable) {
        scope.close();
        listenerInstrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Message message) {
      return AdviceScope.enter(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(throwable);
      }
    }
  }
}
