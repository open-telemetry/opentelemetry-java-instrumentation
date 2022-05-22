/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

public class BatchRecordListener {

  private static final AtomicInteger lastBatchSize = new AtomicInteger();
  private static volatile CountDownLatch messageReceived = new CountDownLatch(2);

  @KafkaListener(
      id = "testBatchListener",
      topics = "testBatchTopic",
      containerFactory = "batchFactory")
  public void listener(List<ConsumerRecord<String, String>> records) {
    lastBatchSize.set(records.size());
    IntStream.range(0, records.size()).forEach(it -> messageReceived.countDown());

    GlobalTraceUtil.runWithSpan("consumer", () -> {});
    records.forEach(
        record -> {
          if (record.value().equals("error")) {
            throw new IllegalArgumentException("boom");
          }
        });
  }

  public static void reset() {
    messageReceived = new CountDownLatch(2);
    lastBatchSize.set(0);
  }

  public static void waitForMessages() throws InterruptedException {
    messageReceived.await(30, TimeUnit.SECONDS);
  }

  public static int getLastBatchSize() {
    return lastBatchSize.get();
  }
}
