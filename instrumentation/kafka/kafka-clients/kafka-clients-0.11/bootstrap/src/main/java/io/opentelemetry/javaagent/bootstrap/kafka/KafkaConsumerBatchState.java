/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Poll provenance and processing ownership for Kafka delivery batches and records.
 *
 * <p>Framework adapters share this state through a virtual field on {@code ConsumerRecords}. Its
 * bootstrap-visible type is part of that field's identity.
 */
public final class KafkaConsumerBatchState implements BooleanSupplier {

  private final boolean applicationPoll;
  private final AtomicBoolean traversalClaimed = new AtomicBoolean();
  private volatile boolean processingOwnedOutsideKafkaClient;

  public KafkaConsumerBatchState(boolean applicationPoll) {
    this.applicationPoll = applicationPoll;
  }

  public void markProcessingOwnedOutsideKafkaClient() {
    processingOwnedOutsideKafkaClient = true;
  }

  public boolean claimFirstTraversal() {
    return traversalClaimed.compareAndSet(false, true);
  }

  @Override
  public boolean getAsBoolean() {
    return applicationPoll && !processingOwnedOutsideKafkaClient;
  }
}
