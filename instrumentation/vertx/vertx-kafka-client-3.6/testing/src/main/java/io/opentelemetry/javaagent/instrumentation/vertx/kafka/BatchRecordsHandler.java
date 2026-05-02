/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.vertx.core.Handler;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class BatchRecordsHandler implements Handler<KafkaConsumerRecords<String, String>> {

  public static final BatchRecordsHandler INSTANCE = new BatchRecordsHandler();

  private static final AtomicInteger lastBatchSize = new AtomicInteger();
  private static volatile CountDownLatch messageReceived = new CountDownLatch(0);

  private BatchRecordsHandler() {}

  @Override
  public void handle(KafkaConsumerRecords<String, String> records) {
    lastBatchSize.set(records.size());
    IntStream.range(0, records.size()).forEach(it -> messageReceived.countDown());

    GlobalTraceUtil.runWithSpan("batch consumer", () -> {});
    for (int i = 0; i < records.size(); ++i) {
      KafkaConsumerRecord<String, String> record = records.recordAt(i);
      if ("error".equals(record.value())) {
        throw new IllegalArgumentException("boom");
      }
    }
  }

  public static void reset(int expectedBatchSize) {
    messageReceived = new CountDownLatch(expectedBatchSize);
    lastBatchSize.set(0);
  }

  public static void waitForMessages() throws InterruptedException {
    assertThat(messageReceived.await(30, SECONDS)).isTrue();
  }

  public static int getLastBatchSize() {
    return lastBatchSize.get();
  }
}
