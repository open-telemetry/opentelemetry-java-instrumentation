/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq

import base.BaseConf
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.remoting.common.RemotingHelper
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.PRODUCER

//TODO add tests for propagationEnabled flag
@Unroll
abstract class AbstractRocketMqClientTest extends InstrumentationSpecification {

  @Shared
  DefaultMQProducer producer

  @Shared
  DefaultMQPushConsumer consumer

  @Shared
  String sharedTopic

  @Shared
  Message msg

  @Shared
  def msgs = new ArrayList<Message>()

  abstract void configureMQProducer(DefaultMQProducer producer)

  abstract void configureMQPushConsumer(DefaultMQPushConsumer consumer)

  def setupSpec() {
    sharedTopic = BaseConf.initTopic()
    msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ").getBytes(RemotingHelper.DEFAULT_CHARSET))
    Message msg1 = new Message(sharedTopic, "TagA", ("hello world a").getBytes())
    Message msg2 = new Message(sharedTopic, "TagB", ("hello world b").getBytes())
    msgs.add(msg1)
    msgs.add(msg2)
    producer = BaseConf.getProducer(BaseConf.nsAddr)
    configureMQProducer(producer)
    consumer = BaseConf.getConsumer(BaseConf.nsAddr, sharedTopic, "*", new TracingMessageListener())
    configureMQPushConsumer(consumer)
  }

  def cleanupSpec() {
    producer?.shutdown()
    consumer?.shutdown()
    BaseConf.deleteTempDir()
  }

  def "test rocketmq produce callback"() {
    when:
    producer.send(msg, new SendCallback() {
      @Override
      void onSuccess(SendResult sendResult) {
      }

      @Override
      void onException(Throwable throwable) {
      }
    })

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name sharedTopic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
        span(1) {
          name sharedTopic + " process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long
          }
        }
        span(2) {
          name "messageListener"
          kind INTERNAL
          childOf span(1)
        }
      }
    }
  }

  def "test rocketmq produce and consume"() {
    when:
    runWithSpan("parent") {
      producer.send(msg)
    }

    then:
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
        }
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
        span(2) {
          name sharedTopic + " process"
          kind CONSUMER
          childOf span(1)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "process"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long
          }
        }
        span(3) {
          name "messageListener"
          kind INTERNAL
          childOf span(2)
        }
      }
    }
  }

  def "test rocketmq produce and batch consume"() {
    setup:
    consumer.setConsumeMessageBatchMaxSize(2)

    when:
    runWithSpan("parent") {
      producer.send(msgs)
    }

    then:
    assertTraces(2) {
      def producerSpan = null

      trace(0, 2) {
        producerSpan = span(1)

        span(0) {
          name "parent"
          kind INTERNAL
        }
        span(1) {
          name sharedTopic + " send"
          kind PRODUCER
          childOf span(0)
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" sharedTopic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
      }

      trace(1, 4) {
        span(0) {
          name "multiple_sources receive"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
          }
        }
        span(1) {
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
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long
          }
          childOf span(0)
          hasLink producerSpan
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
            "messaging.rocketmq.tags" "TagB"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long
          }
          childOf span(0)
          hasLink producerSpan
        }
        span(3) {
          name "messageListener"
          kind INTERNAL
          childOf span(0)
        }
      }
    }
  }
}
