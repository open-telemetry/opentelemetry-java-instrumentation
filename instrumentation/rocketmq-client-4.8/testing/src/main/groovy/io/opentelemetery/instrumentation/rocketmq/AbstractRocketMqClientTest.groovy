/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetery.instrumentation.rocketmq

import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.exception.MQClientException
import org.apache.rocketmq.client.producer.DefaultMQProducer
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.common.message.Message
import org.apache.rocketmq.remoting.common.RemotingHelper
import org.apache.rocketmq.test.listener.AbstractListener
import org.apache.rocketmq.test.listener.rmq.order.RMQOrderListener
import org.apache.rocketmq.test.util.MQRandomUtils
import org.apache.rocketmq.test.util.RandomUtil
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import spock.lang.Shared
import spock.lang.Unroll

@Unroll
abstract class AbstractRocketMqClientTest extends InstrumentationSpecification {

  @Shared
  Network network

  @Shared
  GenericContainer nameserver

  @Shared
  GenericContainer broker

  @Shared
  GenericContainer proxy

  @Shared
  DefaultMQProducer producer

  @Shared
  DefaultMQPushConsumer consumer

  @Shared
  def topic

  @Shared
  Message msg

  @Shared
  def msgs = new ArrayList<Message>()

  abstract void configureMQProducer(DefaultMQProducer producer)

  abstract void configureMQPushConsumer(DefaultMQPushConsumer consumer)

  def setupSpec() {
    network = Network.newNetwork()
    nameserver = new GenericContainer("apacherocketmq/rocketmq:4.6.0")
      .withNetwork(network)
      .withNetworkAliases("nameserver")
      .withExposedPorts(9876)
      .withCommand("./mqnamesrv")
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("rocketmq-nameserver")))
      .waitingFor(Wait.forLogMessage('^The Name Server boot success.*', 1))
    nameserver.start()

    broker = new GenericContainer("apacherocketmq/rocketmq:4.6.0")
      .withExposedPorts(10911, 10912, 10909)
      .withNetwork(network)
      .withNetworkAliases("broker")
      .withEnv("NAMESRV_ADDR", "nameserver:9876")
      .withCommand("./mqbroker --brokerIP1=localhost --autoCreateTopicEnable=true")
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("rocketmq-broker")))
      .waitingFor(Wait.forLogMessage('^The broker.* boot success.*', 1))
    broker.start()

    def nameserverAddress = "${nameserver.getHost()}:${nameserver.getMappedPort(9876)}"
    topic = MQRandomUtils.getRandomTopic()

    msg = new Message(topic, "TagA", ("Hello RocketMQ").getBytes(RemotingHelper.DEFAULT_CHARSET))
    Message msg1 = new Message(topic, "TagA", ("hello world a").getBytes())
    Message msg2 = new Message(topic, "TagB", ("hello world b").getBytes())
    msgs.add(msg1)
    msgs.add(msg2)
    producer = getProducer(nameserverAddress)
    configureMQProducer(producer)
    consumer = getConsumer(nameserverAddress, topic, "*", new RMQOrderListener())
    configureMQPushConsumer(consumer)
  }

  def cleanupSpec() {
    producer.shutdown()
    consumer.shutdown()
    broker.stop()
    nameserver.stop()
    network.close()
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
      trace(0, 1) {
        span(0) {
          name topic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
      }
    }
  }

  def "test rocketmq produce and consume"() {
    when:
    runUnderTrace("parent") {
      producer.send(msg)
    }
    then:
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent")
        span(1) {
          name topic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.tags" "TagA"
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
        span(2) {
          name topic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
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
      }
    }
  }

  def "test rocketmq produce and batch consume"() {
    setup:
    consumer.setConsumeMessageBatchMaxSize(2)
    when:
    runUnderTrace("parent") {
      producer.send(msgs)
    }
    then:
    assertTraces(2) {
      def itemStepSpan = null

      trace(0, 2) {
        itemStepSpan = span(1)

        basicSpan(it, 0, "parent")
        span(1) {
          name topic + " send"
          kind PRODUCER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
            "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "topic"
            "${SemanticAttributes.MESSAGING_MESSAGE_ID.key}" String
            "messaging.rocketmq.broker_address" String
            "messaging.rocketmq.send_result" "SEND_OK"
          }
        }
      }

      trace(1, 3) {
        span(0) {
          name "multiple_sources receive"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_OPERATION.key}" "receive"
          }
        }
        span(1) {
          name topic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
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
          hasLink itemStepSpan
        }
        span(2) {
          name topic + " process"
          kind CONSUMER
          attributes {
            "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rocketmq"
            "${SemanticAttributes.MESSAGING_DESTINATION.key}" topic
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
          hasLink itemStepSpan
        }
      }
    }
  }

  static DefaultMQPushConsumer getConsumer(
    String nsAddr, String topic, String subExpression, AbstractListener listener)
    throws MQClientException {
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("consumerGroup")
    consumer.setInstanceName(RandomUtil.getStringByUUID())
    consumer.setNamesrvAddr(nsAddr)
    consumer.subscribe(topic, subExpression)
    consumer.setMessageListener(listener)
    consumer.start()
    return consumer
  }

  static DefaultMQProducer getProducer(String ns) throws MQClientException {
    DefaultMQProducer producer = new DefaultMQProducer(RandomUtil.getStringByUUID())
    producer.setInstanceName(UUID.randomUUID().toString())
    producer.setNamesrvAddr(ns)
    producer.start()
    return producer
  }
}
