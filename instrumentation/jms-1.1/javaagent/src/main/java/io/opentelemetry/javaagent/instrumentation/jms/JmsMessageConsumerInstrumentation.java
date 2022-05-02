/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.JmsSingletons.consumerInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.jms.Message;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JmsMessageConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.MessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic()),
        JmsMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("receiveNoWait").and(takesArguments(0)).and(isPublic()),
        JmsMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter Timer timer,
        @Advice.Return Message message,
        @Advice.Thrown Throwable throwable) {
      if (message == null) {
        // Do not create span when no message is received
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      MessageWithDestination request = MessageWithDestination.create(message, null, timer);

      if (consumerInstrumenter().shouldStart(parentContext, request)) {
        Context context = consumerInstrumenter().start(parentContext, request);
        consumerInstrumenter().end(context, request, null, throwable);
      }
    }
  }
}
