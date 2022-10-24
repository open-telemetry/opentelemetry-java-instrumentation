/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclientjava.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;
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
      List<SettableFuture<SendReceiptImpl>> futures = FutureConverter.covert(future0, count);
      for (int i = 0; i < count; i++) {
        PublishingMessageImpl message = messages.get(i);

        // Try to extract parent context.
        VirtualField<PublishingMessageImpl, Context> virtualField =
            VirtualField.find(PublishingMessageImpl.class, Context.class);
        Context parentContext = virtualField.get(message);
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
            new SpanFinishingCallback<>(instrumenter, context, message),
            MoreExecutors.directExecutor());
      }
    }
  }

  /** Future converter, which covert future of list into list of future. */
  public static class FutureConverter {

    private FutureConverter() {}

    public static <T> List<SettableFuture<T>> covert(SettableFuture<List<T>> future, int num) {
      List<SettableFuture<T>> futures = new ArrayList<>(num);
      for (int i = 0; i < num; i++) {
        SettableFuture<T> f = SettableFuture.create();
        futures.add(f);
      }
      ListFutureCallback<T> futureCallback = new ListFutureCallback<>(futures);
      Futures.addCallback(future, futureCallback, MoreExecutors.directExecutor());
      return futures;
    }
  }

  public static class ListFutureCallback<T> implements FutureCallback<List<T>> {
    private final List<SettableFuture<T>> futures;

    public ListFutureCallback(List<SettableFuture<T>> futures) {
      this.futures = futures;
    }

    @Override
    public void onSuccess(List<T> result) {
      for (int i = 0; i < result.size(); i++) {
        futures.get(i).set(result.get(i));
      }
    }

    @Override
    public void onFailure(Throwable t) {
      for (SettableFuture<T> future : futures) {
        future.setException(t);
      }
    }
  }

  public static class SpanFinishingCallback<T> implements FutureCallback<T> {
    private final Instrumenter<PublishingMessageImpl, SendReceiptImpl> instrumenter;
    private final Context context;
    private final PublishingMessageImpl message;

    public SpanFinishingCallback(
        Instrumenter<PublishingMessageImpl, SendReceiptImpl> instrumenter,
        Context context,
        PublishingMessageImpl message) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.message = message;
    }

    @Override
    public void onSuccess(T result) {
      SendReceiptImpl sendReceipt = (SendReceiptImpl) result;
      instrumenter.end(context, message, sendReceipt, null);
    }

    @Override
    public void onFailure(Throwable t) {
      instrumenter.end(context, message, null, t);
    }
  }
}
