/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerRequest {

  private final MessageExt message;
  private final String consumerGroup;
  private final int batchSize;
  private final String namespace;

  RocketMqConsumerRequest(
      MessageExt message, String consumerGroup, int batchSize, @Nullable String namespace) {
    this.message = message;
    this.consumerGroup = RocketMqNamespaceUtil.withoutNamespace(consumerGroup, namespace);
    this.batchSize = batchSize;
    this.namespace = namespace == null ? "" : namespace;
  }

  MessageExt getMessage() {
    return message;
  }

  String getConsumerGroup() {
    return consumerGroup;
  }

  int getBatchSize() {
    return batchSize;
  }

  String getNamespace() {
    return namespace;
  }
}
