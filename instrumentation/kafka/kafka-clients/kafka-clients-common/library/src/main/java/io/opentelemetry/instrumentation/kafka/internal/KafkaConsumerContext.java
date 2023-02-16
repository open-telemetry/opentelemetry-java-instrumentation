/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.Context;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaConsumerContext {
  private final Context context;
  private final String consumerGroup;
  private final String clientId;

  public KafkaConsumerContext(Context context, String consumerGroup, String clientId) {
    this.context = context;
    this.consumerGroup = consumerGroup;
    this.clientId = clientId;
  }

  public Context getContext() {
    return context;
  }

  public String getConsumerGroup() {
    return consumerGroup;
  }

  public String getClientId() {
    return clientId;
  }
}
