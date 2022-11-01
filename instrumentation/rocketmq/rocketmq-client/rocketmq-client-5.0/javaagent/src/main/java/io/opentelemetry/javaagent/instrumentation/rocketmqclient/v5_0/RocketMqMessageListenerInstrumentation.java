/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.message.MessageView;

/** Only for {@link org.apache.rocketmq.client.apis.consumer.PushConsumer}. */
final class RocketMqMessageListenerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Instrument ConsumerService instead of MessageListener because lambda could not be enhanced.
    return named("org.apache.rocketmq.client.java.impl.consumer.ConsumeService");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                isPublic()
                    .and(takesArguments(5))
                    .and(takesArgument(0, named("org.apache.rocketmq.client.java.misc.ClientId")))
                    .and(
                        takesArgument(
                            1, named("org.apache.rocketmq.client.apis.consumer.MessageListener")))
                    .and(takesArgument(2, ThreadPoolExecutor.class))
                    .and(
                        takesArgument(
                            3, named("org.apache.rocketmq.client.java.hook.MessageInterceptor")))
                    .and(takesArgument(4, ScheduledExecutorService.class))),
        RocketMqMessageListenerInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 1, readOnly = false) MessageListener messageListener) {
      // Replace messageListener by wrapper.
      messageListener = new MessageListenerWrapper(messageListener);
    }
  }

  public static class MessageListenerWrapper implements MessageListener {
    private final MessageListener delegator;

    public MessageListenerWrapper(MessageListener delegator) {
      this.delegator = delegator;
    }

    @Override
    public ConsumeResult consume(MessageView messageView) {
      Context parentContext = VirtualFieldStore.getContextByMessage(messageView);
      if (parentContext == null) {
        parentContext = Context.current();
      }
      Instrumenter<MessageView, ConsumeResult> processInstrumenter =
          RocketMqSingletons.consumerProcessInstrumenter();
      if (!processInstrumenter.shouldStart(parentContext, messageView)) {
        return delegator.consume(messageView);
      }
      Context context = processInstrumenter.start(parentContext, messageView);
      try (Scope ignored = context.makeCurrent()) {
        ConsumeResult consumeResult = delegator.consume(messageView);
        processInstrumenter.end(context, messageView, consumeResult, null);
        return consumeResult;
      } catch (Throwable t) {
        processInstrumenter.end(context, messageView, null, t);
        throw t;
      }
    }
  }
}
