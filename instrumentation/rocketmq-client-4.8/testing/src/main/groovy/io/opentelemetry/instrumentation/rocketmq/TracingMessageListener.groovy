/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runInternalSpan

import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly
import org.apache.rocketmq.common.message.MessageExt

class TracingMessageListener implements MessageListenerOrderly {
  @Override
  ConsumeOrderlyStatus consumeMessage(List<MessageExt> list, ConsumeOrderlyContext consumeOrderlyContext) {
    runInternalSpan("messageListener")
    return ConsumeOrderlyStatus.SUCCESS
  }
}
