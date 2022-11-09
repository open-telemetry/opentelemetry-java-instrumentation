/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.SettableFuture;

final class RocketMqProducerInstrumentation implements TypeInstrumentation {

  /** Match the implementation of RocketMQ producer. */
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.java.impl.producer.ProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send0"))
            .and(isPrivate())
            .and(takesArguments(6))
            .and(
                takesArgument(
                    0,
                    named(
                        "org.apache.rocketmq.shaded.com.google.common.util.concurrent.SettableFuture")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("org.apache.rocketmq.client.java.message.MessageType")))
            .and(takesArgument(3, List.class))
            .and(takesArgument(4, List.class))
            .and(takesArgument(5, int.class)),
        RocketMqProducerInstrumentation.class.getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) SettableFuture<List<SendReceiptImpl>> future0,
        @Advice.Argument(4) List<PublishingMessageImpl> messages) {
      Instrumenter<PublishingMessageImpl, SendReceiptImpl> instrumenter =
          RocketMqSingletons.producerInstrumenter();
      int count = messages.size();
      List<SettableFuture<SendReceiptImpl>> futures = FutureConverter.convert(future0, count);
      for (int i = 0; i < count; i++) {
        PublishingMessageImpl message = messages.get(i);

        // Try to extract parent context.
        Context parentContext = VirtualFieldStore.getContextByMessage(message);
        if (parentContext == null) {
          parentContext = Context.current();
        }

        Span span = Span.fromContext(parentContext);
        if (!span.getSpanContext().isValid()) {
          parentContext = Context.current();
        }

        SettableFuture<SendReceiptImpl> future = futures.get(i);
        if (!instrumenter.shouldStart(parentContext, message)) {
          return;
        }
        Context context = instrumenter.start(parentContext, message);
        Futures.addCallback(
            future,
            new SendSpanFinishingCallback(instrumenter, context, message),
            MoreExecutors.directExecutor());
      }
    }
  }
}
