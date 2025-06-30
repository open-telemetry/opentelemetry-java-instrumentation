/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import java.util.List;
import javax.annotation.Nullable;

abstract class AbstractKafkaConsumerRequest extends AbstractKafkaRequest {

  @Nullable private final String consumerGroup;
  @Nullable private final String clientId;

  protected AbstractKafkaConsumerRequest(
      @Nullable String consumerGroup,
      @Nullable String clientId,
      @Nullable List<String> bootstrapServers) {
    super(bootstrapServers);
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
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
