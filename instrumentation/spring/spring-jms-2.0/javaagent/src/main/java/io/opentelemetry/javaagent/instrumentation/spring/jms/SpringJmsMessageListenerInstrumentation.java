/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.spring.jms.SpringJmsSingletons.listenerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.jms.Message;
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
        named("onMessage").and(takesArguments(2)).and(isPublic()),
        SpringJmsMessageListenerInstrumentation.class.getName() + "$MessageListenerAdvice");
  }

  @SuppressWarnings("unused")
  public static class MessageListenerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Message message,
        @Advice.Local("otelRequest") MessageWithDestination request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = MessageWithDestination.create(message);

      if (!listenerInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = listenerInstrumenter().start(parentContext, request);
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
      scope.close();
      listenerInstrumenter().end(context, request, null, throwable);
    }
  }
}
