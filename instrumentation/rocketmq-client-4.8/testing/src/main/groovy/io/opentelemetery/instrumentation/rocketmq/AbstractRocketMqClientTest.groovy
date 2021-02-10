/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetery.instrumentation.rocketmq

import base.BaseConf
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.remoting.common.RemotingHelper
import org.apache.rocketmq.test.client.rmq.RMQNormalConsumer
import org.apache.rocketmq.test.client.rmq.RMQNormalProducer
import org.apache.rocketmq.test.factory.ProducerFactory
import org.apache.rocketmq.test.listener.rmq.concurrent.RMQNormalListener
import org.apache.rocketmq.test.listener.rmq.order.RMQOrderListener
import spock.lang.Shared
import spock.lang.Unroll
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

@Unroll
abstract class AbstractRocketMqClientTest extends InstrumentationSpecification{

  @Shared
  RMQNormalConsumer consumer;

  @Shared
  RMQNormalProducer producer;

  @Shared
  DefaultMQProducer defaultMQProducer;

  @Shared
  String sharedTopic;

  @Shared
  String brokerAddr;

  @Shared
  Message msg;

  @Shared
  int consumeTime = 5000;

  @Shared
  BaseConf baseConf =new BaseConf();

  def setup() {
    sharedTopic =baseConf.initTopic();
    brokerAddr =baseConf.getBrokerAddr()
    msg = new Message(sharedTopic, "TagA", ("Hello RocketMQ").getBytes(RemotingHelper.DEFAULT_CHARSET));
  }

  def "test rocketmq produce callback"() {
    setup:
    defaultMQProducer = ProducerFactory.getRMQProducer(baseConf.nsAddr);
    when:
    runUnderTrace("parent") {
      defaultMQProducer.send(msg, new SendCallback() {
        @Override
        void onSuccess(SendResult sendResult) {
        }

        @Override
        void onException(Throwable throwable) {
        }
      });

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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.callback_result" "SEND_OK"
          }
        }
      }
      cleanup:
      defaultMQProducer.shutdown()
    }
  }

  def "test rocketmq produce and concurrently consume"() {
    setup:
    producer = baseConf.getProducer(baseConf.nsAddr, sharedTopic);
    consumer = baseConf.getConsumer(baseConf.nsAddr, sharedTopic, "*", new RMQNormalListener());
    when:
    runUnderTrace("parent") {
      producer.send(msg);
      consumer.getListener().waitForMessageConsume(producer.getAllMsgBody(), consumeTime);
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.consume_concurrently_status" "CONSUME_SUCCESS"
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }
      cleanup:
      producer.shutdown()
      consumer.shutdown()

    }
  }


  def "test rocketmq produce and orderly consume"() {
    setup:
    producer = baseConf.getProducer(baseConf.nsAddr, sharedTopic);
    consumer = baseConf.getConsumer(baseConf.nsAddr, sharedTopic, "*", new RMQOrderListener());
    when:
    runUnderTrace("parent") {
      producer.send(msg);
      consumer.getListener().waitForMessageConsume(producer.getAllMsgBody(), consumeTime);
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
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
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" brokerAddr
            "messaging.rocketmq.consume_orderly_status" "SUCCESS"
            "messaging.rocketmq.queue_id" Long
            "messaging.rocketmq.queue_offset" Long

          }
        }
      }
      cleanup:
      producer.shutdown()
      consumer.shutdown()

    }
  }
}

