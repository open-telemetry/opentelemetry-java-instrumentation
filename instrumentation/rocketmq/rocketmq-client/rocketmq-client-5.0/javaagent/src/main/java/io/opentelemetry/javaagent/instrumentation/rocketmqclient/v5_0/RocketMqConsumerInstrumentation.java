/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import apache.rocketmq.v2.ReceiveMessageRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.client.java.message.MessageViewImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.FutureCallback;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;

final class RocketMqConsumerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.java.impl.consumer.ConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("receiveMessage"))
            .and(takesArguments(3))
            .and(takesArgument(0, named("apache.rocketmq.v2.ReceiveMessageRequest")))
            .and(takesArgument(1, named("org.apache.rocketmq.client.java.route.MessageQueueImpl")))
            .and(takesArgument(2, named("java.time.Duration"))),
        RocketMqConsumerInstrumentation.class.getName() + "$ReceiveMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Timer onStart() {
      return Timer.start();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) ReceiveMessageRequest request,
        @Advice.Enter Timer timer,
        @Advice.Return ListenableFuture<ReceiveMessageResult> future) {
      String consumerGroup = request.getGroup().getName();
      SpanFinishingCallback spanFinishingCallback = new SpanFinishingCallback(request, timer);
      Futures.addCallback(future, spanFinishingCallback, MoreExecutors.directExecutor());
    }
  }

  public static class SpanFinishingCallback implements FutureCallback<ReceiveMessageResult> {

    private final ReceiveMessageRequest request;
    private final Timer timer;

    public SpanFinishingCallback(ReceiveMessageRequest request, Timer timer) {
      this.request = request;
      this.timer = timer;
    }

    @Override
    public void onSuccess(ReceiveMessageResult receiveMessageResult) {
      List<MessageViewImpl> messageViews = receiveMessageResult.getMessageViewImpls();
      // Don't create spans when no messages were received.
      if (messageViews.isEmpty()) {
        return;
      }
      String consumerGroup = request.getGroup().getName();
      for (MessageViewImpl messageView : messageViews) {
        VirtualFieldStore.setConsumerGroupByMessage(messageView, consumerGroup);
      }
      Instrumenter<ReceiveMessageRequest, List<MessageView>> receiveInstrumenter =
          RocketMqSingletons.consumerReceiveInstrumenter();
      Context parentContext = Context.current();
      if (receiveInstrumenter.shouldStart(parentContext, request)) {
        Context context =
            InstrumenterUtil.startAndEnd(
                receiveInstrumenter,
                parentContext,
                request,
                null,
                null,
                timer.startTime(),
                timer.now());
        for (MessageViewImpl messageView : messageViews) {
          VirtualFieldStore.setContextByMessage(messageView, context);
        }
      }
    }

    @Override
    public void onFailure(Throwable throwable) {
      Instrumenter<ReceiveMessageRequest, List<MessageView>> receiveInstrumenter =
          RocketMqSingletons.consumerReceiveInstrumenter();
      Context parentContext = Context.current();
      if (receiveInstrumenter.shouldStart(parentContext, request)) {
        InstrumenterUtil.startAndEnd(
            receiveInstrumenter,
            parentContext,
            request,
            null,
            throwable,
            timer.startTime(),
            timer.now());
      }
    }
  }
}
