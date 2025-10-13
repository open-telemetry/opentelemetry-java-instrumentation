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
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;
import javax.annotation.Nullable;
import javax.jms.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JmsMessageListenerInstrumentation implements TypeInstrumentation {

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
        JmsMessageListenerInstrumentation.class.getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    public static class AdviceScope {
      private final MessageWithDestination messageWithDestination;
      private final Context context;
      private final Scope scope;

      private AdviceScope(
          MessageWithDestination messageWithDestination, Context context, Scope scope) {
        this.messageWithDestination = messageWithDestination;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Message message) {
        Context parentContext = Context.current();
        MessageWithDestination messageWithDestination =
            MessageWithDestination.create(JavaxMessageAdapter.create(message), null);

        if (!consumerProcessInstrumenter().shouldStart(parentContext, messageWithDestination)) {
          return null;
        }

        Context context =
            consumerProcessInstrumenter().start(parentContext, messageWithDestination);
        return new AdviceScope(messageWithDestination, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        consumerProcessInstrumenter().end(context, messageWithDestination, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Message message) {
      return AdviceScope.start(message);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
