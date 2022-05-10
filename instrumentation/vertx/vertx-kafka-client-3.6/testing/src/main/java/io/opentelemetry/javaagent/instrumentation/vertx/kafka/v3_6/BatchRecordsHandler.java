/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.vertx.core.Handler;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class BatchRecordsHandler implements Handler<KafkaConsumerRecords<String, String>> {

  public static final BatchRecordsHandler INSTANCE = new BatchRecordsHandler();

  private static final AtomicInteger lastBatchSize = new AtomicInteger();
  private static volatile CountDownLatch messageReceived = new CountDownLatch(2);

  private BatchRecordsHandler() {}

  @Override
  public void handle(KafkaConsumerRecords<String, String> records) {
    lastBatchSize.set(records.size());
    IntStream.range(0, records.size()).forEach(it -> messageReceived.countDown());

    GlobalTraceUtil.runWithSpan("batch consumer", () -> {});
    for (int i = 0; i < records.size(); ++i) {
      KafkaConsumerRecord<String, String> record = records.recordAt(i);
      if (record.value().equals("error")) {
        throw new IllegalArgumentException("boom");
      }
    }
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
