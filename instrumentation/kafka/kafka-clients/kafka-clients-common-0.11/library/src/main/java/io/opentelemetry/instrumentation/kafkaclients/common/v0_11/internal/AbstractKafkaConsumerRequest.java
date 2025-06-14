/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import javax.annotation.Nullable;

abstract class AbstractKafkaConsumerRequest {

  @Nullable private final String consumerGroup;
  @Nullable private final String clientId;
  @Nullable private final String bootstrapServers;

  AbstractKafkaConsumerRequest(String consumerGroup, String clientId, String bootstrapServers) {
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
    this.bootstrapServers = bootstrapServers;
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
  public String getBootstrapServers() {
    return bootstrapServers;
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
