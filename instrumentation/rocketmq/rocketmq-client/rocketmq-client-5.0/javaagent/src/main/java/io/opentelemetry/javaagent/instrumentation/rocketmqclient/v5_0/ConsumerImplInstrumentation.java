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
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.java.impl.consumer.ReceiveMessageResult;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.Futures;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.MoreExecutors;

final class ConsumerImplInstrumentation implements TypeInstrumentation {
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
        ConsumerImplInstrumentation.class.getName() + "$ReceiveMessageAdvice");
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
      ReceiveSpanFinishingCallback spanFinishingCallback =
          new ReceiveSpanFinishingCallback(request, timer);
      Futures.addCallback(future, spanFinishingCallback, MoreExecutors.directExecutor());
    }
  }
}
