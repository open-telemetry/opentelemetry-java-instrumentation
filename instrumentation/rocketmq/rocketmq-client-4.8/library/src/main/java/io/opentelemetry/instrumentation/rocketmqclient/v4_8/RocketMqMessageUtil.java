/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.logging.Level.WARNING;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.Message;

final class RocketMqMessageUtil {

  private static final Logger logger = Logger.getLogger(RocketMqMessageUtil.class.getName());
  @Nullable private static final Method batchEncodeMethod = getBatchEncodeMethod();

  @Nullable
  private static Method getBatchEncodeMethod() {
    try {
      return Class.forName(
              "org.apache.rocketmq.common.message.MessageBatch",
              false,
              Message.class.getClassLoader())
          .getMethod("encode");
    } catch (ReflectiveOperationException ignored) {
      // MessageBatch is absent in the oldest supported RocketMQ versions.
      return null;
    }
  }

  static boolean isBatch(@Nullable Message message) {
    // Avoid linking MessageBatch because the oldest supported RocketMQ version predates that class.
    return message != null
        && message.getClass().getName().equals("org.apache.rocketmq.common.message.MessageBatch");
  }

  static void reencodeBatch(@Nullable Message message) {
    if (batchEncodeMethod == null || message == null || !isBatch(message)) {
      return;
    }
    try {
      // The send hook runs after RocketMQ has encoded the batch body.
      message.setBody((byte[]) batchEncodeMethod.invoke(message));
    } catch (ReflectiveOperationException e) {
      logger.log(WARNING, "Failed to encode RocketMQ batch after context injection", e);
    }
  }

  private RocketMqMessageUtil() {}
}
