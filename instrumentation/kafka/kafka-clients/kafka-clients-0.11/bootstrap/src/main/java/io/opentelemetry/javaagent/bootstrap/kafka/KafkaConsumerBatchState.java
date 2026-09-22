/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import java.util.function.BooleanSupplier;

/**
 * Processing selection for Kafka delivery batches and records.
 *
 * <p>This batch-named compatibility API is shared with framework adapters through a virtual field
 * on {@code ConsumerRecords}. Its bootstrap-visible type is part of that field's identity.
 */
public final class KafkaConsumerBatchState implements BooleanSupplier {

  private final boolean applicationPoll;
  private volatile boolean frameworkProcessingSelected;

  public KafkaConsumerBatchState(boolean applicationPoll) {
    this.applicationPoll = applicationPoll;
  }

  public void claimProcessSpan() {
    frameworkProcessingSelected = true;
  }

  @Override
  public boolean getAsBoolean() {
    return applicationPoll && !frameworkProcessingSelected;
  }
}
