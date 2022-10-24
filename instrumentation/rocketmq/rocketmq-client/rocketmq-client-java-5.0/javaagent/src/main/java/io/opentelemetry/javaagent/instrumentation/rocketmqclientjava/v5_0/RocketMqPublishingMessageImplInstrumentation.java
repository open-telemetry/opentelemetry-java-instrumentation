/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclientjava.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

final class RocketMqPublishingMessageImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.java.message.PublishingMessageImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("org.apache.rocketmq.client.apis.message.Message")))
            .and(
                takesArgument(
                    1, named("org.apache.rocketmq.client.java.impl.producer.PublishingSettings")))
            .and(takesArgument(2, boolean.class)),
        RocketMqPublishingMessageImplInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    /**
     * The constructor of {@link PublishingMessageImpl} is always called in the same thread that
     * user invoke {@link Producer#send(Message)}/{@link Producer#sendAsync(Message)}/{@link
     * Producer#send(Message, Transaction)}. Store the {@link Context} here and fetch it in {@link
     * RocketMqProducerInstrumentation}.
     */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This PublishingMessageImpl message) {
      VirtualField<PublishingMessageImpl, Context> virtualField =
          VirtualField.find(PublishingMessageImpl.class, Context.class);
      Context context = virtualField.get(message);
      if (context == null) {
        context = Context.current();
        virtualField.set(message, context);
      }
    }
  }
}
