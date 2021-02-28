/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetery.instrumentation.rocketmq

import base.BaseConf
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.remoting.common.RemotingHelper
import org.apache.rocketmq.test.listener.rmq.concurrent.RMQNormalListener
import org.apache.rocketmq.test.listener.rmq.order.RMQOrderListener
import spock.lang.Shared
import spock.lang.Unroll
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

@Unroll
abstract class AbstractRocketMqClientTest extends InstrumentationSpecification{

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

  def "test rocketmq produce callback"() {
    setup:
    producer = BaseConf.getRMQProducer(BaseConf.nsAddr)
    when:
    runUnderTrace("parent") {
      producer.send(msg, new SendCallback() {
        @Override
        void onSuccess(SendResult sendResult) {
        }

        @Override
        void onException(Throwable throwable) {
        }
      })

    }
    then:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent")
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.send_result" "SEND_OK"

          }
        }
      }
      cleanup:
      BaseConf.deleteTempDir()
    }
  }

  def "test rocketmq produce and concurrently consume"() {
    setup:
    producer = BaseConf.getRMQProducer(BaseConf.nsAddr)
    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", new RMQNormalListener())
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.send_result" "SEND_OK"
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }
      cleanup:
      producer.shutdown()
      consumer.shutdown()
      BaseConf.deleteTempDir()
    }
  }


  def "test rocketmq produce and orderly consume"() {
    setup:
    producer = BaseConf.getRMQProducer(BaseConf.nsAddr)
    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", new RMQOrderListener())
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.send_result" "SEND_OK"
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long
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

