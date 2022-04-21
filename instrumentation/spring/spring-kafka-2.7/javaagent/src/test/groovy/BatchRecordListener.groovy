/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BatchRecordListener {
  static AtomicInteger lastBatchSize = new AtomicInteger()
  static CountDownLatch messageReceived = new CountDownLatch(2)

  @KafkaListener(id = "testBatchListener", topics = "testBatchTopic", containerFactory = "batchFactory")
  void listener(List<ConsumerRecord<String, String>> records) {
    lastBatchSize.set(records.size())
    records.size().times {
      messageReceived.countDown()
    }

    GlobalTraceUtil.runWithSpan("consumer") {}
    records.forEach({ record ->
      if (record.value() == "error") {
        throw new IllegalArgumentException("boom")
      }
    })
  }

  static void reset() {
    messageReceived = new CountDownLatch(2)
    lastBatchSize.set(0)
  }

  static void waitForMessages() {
    messageReceived.await(30, TimeUnit.SECONDS)
  }

  static int getLastBatchSize() {
    return lastBatchSize.get()
  }
}
