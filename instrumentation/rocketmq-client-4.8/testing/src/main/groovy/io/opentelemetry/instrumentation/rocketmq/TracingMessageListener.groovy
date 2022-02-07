/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly
import org.apache.rocketmq.common.message.MessageExt

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan

class TracingMessageListener implements MessageListenerOrderly {
  private AtomicInteger lastBatchSize = new AtomicInteger()
  private CountDownLatch messageReceived = new CountDownLatch(1)

  @Override
  ConsumeOrderlyStatus consumeMessage(List<MessageExt> list, ConsumeOrderlyContext consumeOrderlyContext) {
    lastBatchSize.set(list.size())
    messageReceived.countDown()
    runWithSpan("messageListener") {}
    return ConsumeOrderlyStatus.SUCCESS
  }

  void reset() {
    messageReceived = new CountDownLatch(1)
    lastBatchSize.set(0)
  }

  void waitForMessages() {
    messageReceived.await()
  }

  int getLastBatchSize() {
    return lastBatchSize.get()
  }
}
