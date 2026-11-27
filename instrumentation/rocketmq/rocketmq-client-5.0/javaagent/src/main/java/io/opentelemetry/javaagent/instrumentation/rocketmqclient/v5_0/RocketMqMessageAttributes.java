/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;

final class RocketMqMessageAttributes {
  private static final Logger logger = Logger.getLogger(RocketMqMessageAttributes.class.getName());

  static final AttributeKey<String> ROCKETMQ_MESSAGE_LITE_TOPIC =
      AttributeKey.stringKey("rocketmq.message.lite_topic");
  static final AttributeKey<Long> ROCKETMQ_MESSAGE_PRIORITY =
      AttributeKey.longKey("rocketmq.message.priority");

  private static final MethodType GETTER_TYPE = MethodType.methodType(Optional.class);

  @Nullable
  private static final MethodHandle MESSAGE_LITE_TOPIC = findGetter(Message.class, "getLiteTopic");

  @Nullable
  private static final MethodHandle MESSAGE_VIEW_LITE_TOPIC =
      findGetter(MessageView.class, "getLiteTopic");

  @Nullable
  private static final MethodHandle MESSAGE_PRIORITY = findGetter(Message.class, "getPriority");

  @Nullable
  private static final MethodHandle MESSAGE_VIEW_PRIORITY =
      findGetter(MessageView.class, "getPriority");

  @Nullable
  static String getLiteTopic(Message message) {
    return (String) read(MESSAGE_LITE_TOPIC, message);
  }

  @Nullable
  static String getLiteTopic(MessageView message) {
    return (String) read(MESSAGE_VIEW_LITE_TOPIC, message);
  }

  @Nullable
  static Long getPriority(Message message) {
    Integer priority = (Integer) read(MESSAGE_PRIORITY, message);
    return priority == null ? null : priority.longValue();
  }

  @Nullable
  static Long getPriority(MessageView message) {
    Integer priority = (Integer) read(MESSAGE_VIEW_PRIORITY, message);
    return priority == null ? null : priority.longValue();
  }

  @Nullable
  private static MethodHandle findGetter(Class<?> declaringType, String name) {
    try {
      return MethodHandles.publicLookup().findVirtual(declaringType, name, GETTER_TYPE);
    } catch (NoSuchMethodException ignored) {
      // The getters were added in 5.1 (LiteTopic) and 5.2 (priority).
      return null;
    } catch (Throwable t) {
      logger.log(FINE, "Error finding RocketMQ message attribute getter", t);
      return null;
    }
  }

  @Nullable
  private static Object read(@Nullable MethodHandle getter, Object message) {
    if (getter == null) {
      return null;
    }
    try {
      return ((Optional<?>) getter.invoke(message)).orElse(null);
    } catch (Throwable t) {
      logger.log(FINE, "Error reading RocketMQ message attribute", t);
      return null;
    }
  }

  private RocketMqMessageAttributes() {}
}
