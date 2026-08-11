/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import java.util.Map;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;

public class KafkaCommitCallback implements OffsetCommitCallback {

  @Nullable private final OffsetCommitCallback callback;
  private final KafkaCommitAsyncTracing.TracingState tracingState;

  public KafkaCommitCallback(
      @Nullable OffsetCommitCallback callback, KafkaCommitAsyncTracing.TracingState tracingState) {
    this.callback = callback;
    this.tracingState = tracingState;
  }

  @Override
  public void onComplete(
      Map<TopicPartition, OffsetAndMetadata> offsets, @Nullable Exception exception) {
    tracingState.end(exception);
    if (callback != null) {
      callback.onComplete(offsets, exception);
    }
  }
}
