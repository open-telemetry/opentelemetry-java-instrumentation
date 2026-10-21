/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;

abstract class AbstractKafkaConsumerRequest {

  @Nullable private final String consumerGroup;
  @Nullable private final String clientId;
  @Nullable private final String clusterId;

  AbstractKafkaConsumerRequest(
      @Nullable String consumerGroup, @Nullable String clientId, @Nullable String clusterId) {
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
    this.clusterId = clusterId;
  }

  @Nullable
  public String getConsumerGroup() {
    return consumerGroup;
  }

  @Nullable
  public String getClientId() {
    return clientId;
  }

  @Nullable
  public String getClusterId() {
    return clusterId;
  }

  @Nullable
  public String getConsumerId() {
    if (consumerGroup != null) {
      if (clientId != null) {
        return consumerGroup + " - " + clientId;
      }
      return consumerGroup;
    }
    return null;
  }
}
