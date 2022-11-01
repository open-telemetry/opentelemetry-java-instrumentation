/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.java.message.MessageImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

final class RocketMqPublishingMessageImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.rocketmq.client.java.message.PublishingMessageImpl",
        "org.apache.rocketmq.client.java.message.MessageImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(isPublic())
            .and(takesArgument(0, named("org.apache.rocketmq.client.apis.message.Message")))
            .and(
                takesArgument(
                    1, named("org.apache.rocketmq.client.java.impl.producer.PublishingSettings")))
            .and(takesArgument(2, boolean.class)),
        RocketMqPublishingMessageImplInstrumentation.class.getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("getProperties")).and(isPublic()),
        RocketMqPublishingMessageImplInstrumentation.class.getName() + "$GetPropertiesAdvice");
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
      VirtualFieldStore.setContextByMessage(message, Context.current());
    }
  }

  @SuppressWarnings("unused")
  public static class GetPropertiesAdvice {
    /** Update the message properties to propagate context recorded by {@link MessageMapSetter}. */
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This MessageImpl messageImpl,
        @Advice.Return(readOnly = false) Map<String, String> properties) {
      if (!(messageImpl instanceof PublishingMessageImpl)) {
        return;
      }
      PublishingMessageImpl message = (PublishingMessageImpl) messageImpl;
      Map<String, String> extraProperties = VirtualFieldStore.getExtraPropertiesByMessage(message);
      if (extraProperties != null) {
        properties.putAll(extraProperties);
      }
    }
  }
}
