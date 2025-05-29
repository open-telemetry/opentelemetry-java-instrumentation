/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

public class SingleRecordListener {
  private int failureCount;

  @KafkaListener(
      id = "testSingleListener",
      topics = "testSingleTopic",
      containerFactory = "singleFactory")
  public void listener(ConsumerRecord<String, String> record) {
    GlobalTraceUtil.runWithSpan("consumer", () -> {});
    if (record.value().equals("error") && failureCount < 2) {
      failureCount++;
      throw new IllegalArgumentException("boom");
    }
  }
}
