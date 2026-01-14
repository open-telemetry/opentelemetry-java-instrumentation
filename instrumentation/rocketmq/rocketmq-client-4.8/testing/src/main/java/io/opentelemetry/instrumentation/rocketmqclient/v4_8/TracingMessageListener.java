/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.common.message.MessageExt;

class TracingMessageListener implements MessageListenerOrderly {

  private final AtomicInteger lastBatchSize = new AtomicInteger();
  private CountDownLatch messageReceived = new CountDownLatch(1);

  @Override
  public ConsumeOrderlyStatus consumeMessage(
      List<MessageExt> list, ConsumeOrderlyContext consumeOrderlyContext) {
    lastBatchSize.set(list.size());
    messageReceived.countDown();
    runWithSpan("messageListener", () -> {});
    return ConsumeOrderlyStatus.SUCCESS;
  }

  void reset() {
    messageReceived = new CountDownLatch(1);
    lastBatchSize.set(0);
  }

  void waitForMessages() throws InterruptedException {
    messageReceived.await(30, TimeUnit.SECONDS);
  }

  int getLastBatchSize() {
    return lastBatchSize.get();
  }
}
