/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Duration;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.ListenableFuture;

final class SimpleConsumerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.java.impl.consumer.SimpleConsumerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("receive0")
            .and(takesArguments(2))
            .and(takesArgument(0, int.class))
            .and(takesArgument(1, Duration.class)),
        getClass().getName() + "$ReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReceiveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static SimpleConsumerReceiveOperation onEnter(
        @Advice.This SimpleConsumer simpleConsumer) {
      return SimpleConsumerReceiveOperation.start(simpleConsumer);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static ListenableFuture<List<MessageView>> onExit(
        @Advice.Enter SimpleConsumerReceiveOperation operation,
        @Advice.Return ListenableFuture<List<MessageView>> future) {
      return operation == null ? future : operation.wrap(future);
    }
  }
}
