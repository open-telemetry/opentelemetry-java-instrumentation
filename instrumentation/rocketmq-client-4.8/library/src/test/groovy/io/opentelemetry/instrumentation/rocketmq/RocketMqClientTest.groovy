/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import base.BaseConf
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.remoting.common.RemotingHelper
import org.apache.rocketmq.test.listener.rmq.order.RMQOrderListener
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

class RocketMqClientTest extends InstrumentationSpecification implements LibraryTestTrait {

  @Shared
  DefaultMQPushConsumer consumer

  @Shared
  DefaultMQProducer producer

  @Shared
  String sharedTopic

  @Shared
  String brokerAddr

  @Shared
  Message msg

  def setup() {
    sharedTopic = BaseConf.initTopic()
    brokerAddr = BaseConf.getBrokerAddr()
    msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ").getBytes(RemotingHelper.DEFAULT_CHARSET))
  }

  def "test rocketmq produce and consume"() {
    setup:
    producer = BaseConf.getProducer(BaseConf.nsAddr)
    producer.getDefaultMQProducerImpl().registerSendMessageHook(new TracingSendMessageHookImpl())

    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", new RMQOrderListener())
    consumer.getDefaultMQPushConsumerImpl().registerConsumeMessageHook(new TracingConsumeMessageHookImpl())
    when:
    runUnderTrace("parent") {
      producer.send(msg)
    }
    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
          }
        }
        span(2) {
          name sharedTopic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
          }
        }
      }
      cleanup:
      producer.shutdown()
      consumer.shutdown()
      BaseConf.deleteTempDir()
    }
  }

}

