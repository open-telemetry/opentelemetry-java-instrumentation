/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.reflect.Method;
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

  @Nullable
  private static final Method MESSAGE_LITE_TOPIC = findMethod(Message.class, "getLiteTopic");

  @Nullable
  private static final Method MESSAGE_VIEW_LITE_TOPIC =
      findMethod(MessageView.class, "getLiteTopic");

  @Nullable private static final Method MESSAGE_PRIORITY = findMethod(Message.class, "getPriority");

  @Nullable
  private static final Method MESSAGE_VIEW_PRIORITY = findMethod(MessageView.class, "getPriority");

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
  private static Method findMethod(Class<?> declaringType, String name) {
    try {
      return declaringType.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      // The getters were added in 5.1 (LiteTopic) and 5.2 (priority).
      return null;
    } catch (Throwable t) {
      logger.log(FINE, "Error finding RocketMQ message attribute getter", t);
      return null;
    }
  }

  @Nullable
  private static Object read(@Nullable Method method, Object message) {
    if (method == null) {
      return null;
    }
    try {
      return ((Optional<?>) method.invoke(message)).orElse(null);
    } catch (Throwable t) {
      logger.log(FINE, "Error reading RocketMQ message attribute", t);
      return null;
    }
  }

  private RocketMqMessageAttributes() {}
}
