/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.Message;

final class RocketMqMessageUtil {

  static boolean isBatch(@Nullable Message message) {
    // Avoid linking MessageBatch because the oldest supported RocketMQ version predates that class.
    return message != null
        && message.getClass().getName().equals("org.apache.rocketmq.common.message.MessageBatch");
  }

  private RocketMqMessageUtil() {}
}
