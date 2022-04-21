/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

class SingleRecordListener {
  @KafkaListener(id = "testSingleListener", topics = "testSingleTopic", containerFactory = "singleFactory")
  void listener(ConsumerRecord<String, String> record) {
    GlobalTraceUtil.runWithSpan("consumer") {}
    if (record.value() == "error") {
      throw new IllegalArgumentException("boom")
    }
  }
}
