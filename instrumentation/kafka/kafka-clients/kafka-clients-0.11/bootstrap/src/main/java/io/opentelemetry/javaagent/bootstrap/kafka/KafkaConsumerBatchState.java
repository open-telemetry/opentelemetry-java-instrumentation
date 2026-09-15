/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import java.util.function.BooleanSupplier;

/** Process telemetry ownership for one Kafka {@code ConsumerRecords} delivery. */
public final class KafkaConsumerBatchState implements BooleanSupplier {

  private final boolean applicationPoll;
  private volatile boolean processSpanClaimed;

  public KafkaConsumerBatchState(boolean applicationPoll) {
    this.applicationPoll = applicationPoll;
  }

  public void claimProcessSpan() {
    processSpanClaimed = true;
  }

  @Override
  public boolean getAsBoolean() {
    return applicationPoll && !processSpanClaimed;
  }
}
