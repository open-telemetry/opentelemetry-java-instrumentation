/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jms.JmsReceiveSpanUtil.createReceiveSpan;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.consumerProcessInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.jms.v1_1.JmsSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jms.JmsConfig;
import io.opentelemetry.javaagent.instrumentation.jms.MessageState;
import io.opentelemetry.javaagent.instrumentation.jms.MessageWithDestination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
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
        named("receive")
            .and(takesArguments(0).or(takesArguments(1)))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        JmsMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    transformer.applyAdviceToMethod(
        named("receiveNoWait")
            .and(takesArguments(0))
            .and(returns(named("javax.jms.Message")))
            .and(isPublic()),
        JmsMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumerAdvice {

    @Advice.OnMethodEnter
    public static Timer onEnter(@Advice.This MessageConsumer consumer) {

      if (JmsConfig.EXPERIMENTAL_CONSUMER_PROCESS_TELEMETRY_ENABLED) {
        VirtualField<MessageConsumer, MessageState> storage =
            VirtualField.find(MessageConsumer.class, MessageState.class);
        MessageState messageState = storage.get(consumer);
        if (messageState != null) {
          messageState.processScope.close();
          consumerProcessInstrumenter().end(messageState.context, messageState.message, null, null);
          storage.set(consumer, null);
        }
      }

      return Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This MessageConsumer consumer,
        @Advice.Enter Timer timer,
        @Advice.Return Message message,
        @Advice.Thrown Throwable throwable) {
      if (message == null) {
        // Do not create span when no message is received
        return;
      }

      Context parentContext = Java8BytecodeBridge.currentContext();
      MessageWithDestination request =
          MessageWithDestination.create(JavaxMessageAdapter.create(message), null);

      Context receiveContext =
          createReceiveSpan(consumerReceiveInstrumenter(), request, timer, throwable);
      if (JmsConfig.EXPERIMENTAL_CONSUMER_PROCESS_TELEMETRY_ENABLED && receiveContext != null) {
        if (consumerProcessInstrumenter().shouldStart(receiveContext, request)) {
          Context processContext = consumerProcessInstrumenter().start(receiveContext, request);
          Scope processScope = processContext.makeCurrent();
          VirtualField<MessageConsumer, MessageState> storage =
              VirtualField.find(MessageConsumer.class, MessageState.class);
          storage.set(consumer, new MessageState(processContext, processScope, request));
        }
      }
    }
  }
}
