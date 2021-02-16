/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq
import io.opentelemetery.instrumentation.rocketmq.AbstractRocketMqClientLibraryTest
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.apache.rocketmq.common.message.Message

class RocketMqClientTest extends AbstractRocketMqClientLibraryTest implements LibraryTestTrait {

  @Override
  void producerIntercept(String addr,Message msg) {
    TracingMessageInterceptor tracingProducerInterceptor=  new TracingMessageInterceptor()
    tracingProducerInterceptor.producerIntercept(addr,msg);
  }

  @Override
  void consumerIntercept(List<Object> msg, String type) {
    TracingMessageInterceptor tracingProducerInterceptor=  new TracingMessageInterceptor()
    if("concurrent".equals(type)){
      tracingProducerInterceptor.consumerConcurrentlyIntercept(msg);
    } else {
      tracingProducerInterceptor.consumerOrderlyIntercept(msg);
    }
  }
}
