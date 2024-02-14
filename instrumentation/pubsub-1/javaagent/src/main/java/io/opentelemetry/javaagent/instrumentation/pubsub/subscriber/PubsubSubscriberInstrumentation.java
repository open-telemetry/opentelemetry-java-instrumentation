/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub.subscriber;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PubsubSubscriberInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.cloud.pubsub.v1.Subscriber");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("newBuilder"))
            .and(isPublic())
            .and(takesArgument(0, named("java.lang.String")))
            .and(takesArgument(1, named("com.google.cloud.pubsub.v1.MessageReceiver"))),
        PubsubSubscriberInstrumentation.class.getName() + "$AddMessageReceiverAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("newBuilder"))
            .and(isPublic())
            .and(takesArgument(0, named("java.lang.String")))
            .and(
                takesArgument(
                    1, named("com.google.cloud.pubsub.v1.MessageReceiverWithAckResponse"))),
        PubsubSubscriberInstrumentation.class.getName()
            + "$AddMessageReceiverWithAckResponseAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddMessageReceiverAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.Argument(0) String subscription,
        @Advice.Argument(value = 1, readOnly = false) MessageReceiver messageReceiver) {
      messageReceiver =
          new TracingMessageReceiver(ReceiveMessageHelper.of(subscription).instrumenter())
              .build(messageReceiver);
    }
  }

  @SuppressWarnings("unused")
  public static class AddMessageReceiverWithAckResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addInterceptor(
        @Advice.Argument(0) String subscription,
        @Advice.Argument(value = 1, readOnly = false)
            MessageReceiverWithAckResponse messageReceiver) {
      messageReceiver =
          new TracingMessageReceiver(ReceiveMessageHelper.of(subscription).instrumenter())
              .buildWithAckResponse(messageReceiver);
    }
  }
}
