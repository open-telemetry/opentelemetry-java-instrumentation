/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.ShutdownSignalException
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR

class RabbitMqTest extends AgentInstrumentationSpecification implements WithRabbitMqTrait {

  Connection conn = connectionFactory.newConnection()
  Channel channel = conn.createChannel()

  def setupSpec() {
    startRabbit()
  }

  def cleanupSpec() {
    stopRabbit()
  }

  def cleanup() {
    try {
      channel.close()
      conn.close()
    } catch (ShutdownSignalException ignored) {
    }
  }

  def "test rabbit publish/get"() {
    setup:
    String queueName = runWithSpan("producer parent") {
      channel.exchangeDeclare(exchangeName, "direct", false)
      String queueName = channel.queueDeclare().getQueue()
      channel.queueBind(queueName, exchangeName, routingKey)
      channel.basicPublish(exchangeName, routingKey, null, "Hello, world!".getBytes())
      return queueName
    }
    GetResponse response = runWithSpan("consumer parent") {
      return channel.basicGet(queueName, true)
    }

    expect:
    new String(response.getBody()) == "Hello, world!"

    and:
    assertTraces(2) {
      SpanData producerSpan

      trace(0, 5) {
        span(0) {
          name "producer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, null, null, null, "exchange.declare", span(0))
        rabbitSpan(it, 2, null, null, null, "queue.declare", span(0))
        rabbitSpan(it, 3, null, null, null, "queue.bind", span(0))
        rabbitSpan(it, 4, exchangeName, routingKey, "send", "$exchangeName", span(0))

        producerSpan = span(4)
      }

      trace(1, 2) {
        span(0) {
          name "consumer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, exchangeName, routingKey, "receive", "<generated>", span(0), producerSpan)
      }
    }

    where:
    exchangeName    | routingKey
    "some-exchange" | "some-routing-key"
  }

  def "test rabbit publish/get default exchange"() {
    setup:
    String queueName = runWithSpan("producer parent") {
      String queueName = channel.queueDeclare().getQueue()
      channel.basicPublish("", queueName, null, "Hello, world!".getBytes())
      return queueName
    }
    GetResponse response = runWithSpan("consumer parent") {
      return channel.basicGet(queueName, true)
    }

    expect:
    new String(response.getBody()) == "Hello, world!"

    and:
    assertTraces(2) {
      SpanData producerSpan

      trace(0, 3) {
        span(0) {
          name "producer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, null, null, null, "queue.declare", span(0))
        rabbitSpan(it, 2, "<default>", null, "send", "<default>", span(0))

        producerSpan = span(2)
      }

      trace(1, 2) {
        span(0) {
          name "consumer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, "<default>", null, "receive", "<generated>", span(0), producerSpan)
      }
    }
  }

  def "test rabbit consume #messageCount messages and setTimestamp=#setTimestamp"() {
    setup:
    channel.exchangeDeclare(exchangeName, "direct", false)
    String queueName = (messageCount % 2 == 0) ?
      channel.queueDeclare().getQueue() :
      channel.queueDeclare("some-queue", false, true, true, null).getQueue()
    channel.queueBind(queueName, exchangeName, "")

    def deliveries = []

    Consumer callback = new DefaultConsumer(channel) {
      @Override
      void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        deliveries << new String(body)
      }
    }

    channel.basicConsume(queueName, callback)

    (1..messageCount).each {
      if (setTimestamp) {
        channel.basicPublish(exchangeName, "",
          new AMQP.BasicProperties.Builder().timestamp(new Date()).build(),
          "msg $it".getBytes())
      } else {
        channel.basicPublish(exchangeName, "", null, "msg $it".getBytes())
      }
    }
    def resource = messageCount % 2 == 0 ? "<generated>" : queueName

    expect:
    assertTraces(4 + messageCount) {
      trace(0, 1) {
        rabbitSpan(it, 0, null, null, null, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, 0, null, null, null, "basic.consume")
      }
      (1..messageCount).each {
        trace(3 + it, 2) {
          rabbitSpan(it, 0, exchangeName, null, "send", "$exchangeName")
          rabbitSpan(it, 1, exchangeName, null, "process", resource, span(0), null, null, null, setTimestamp)
        }
      }
    }

    deliveries == (1..messageCount).collect { "msg $it" }

    where:
    exchangeName    | messageCount | setTimestamp
    "some-exchange" | 1            | false
    "some-exchange" | 2            | false
    "some-exchange" | 3            | false
    "some-exchange" | 4            | false
    "some-exchange" | 1            | true
    "some-exchange" | 2            | true
    "some-exchange" | 3            | true
    "some-exchange" | 4            | true
  }

  def "test rabbit consume error"() {
    setup:
    def error = new FileNotFoundException("Message Error")
    channel.exchangeDeclare(exchangeName, "direct", false)
    String queueName = channel.queueDeclare().getQueue()
    channel.queueBind(queueName, exchangeName, "")

    Consumer callback = new DefaultConsumer(channel) {
      @Override
      void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        throw error
        // Unfortunately this doesn't seem to be observable in the test outside of the span generated.
      }
    }

    channel.basicConsume(queueName, callback)

    channel.basicPublish(exchangeName, "", null, "msg".getBytes())

    expect:
    assertTraces(5) {
      trace(0, 1) {
        rabbitSpan(it, 0, null, null, null, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, 0, null, null, null, "basic.consume")
      }
      trace(4, 2) {
        rabbitSpan(it, 0, exchangeName, null, "send", "$exchangeName")
        rabbitSpan(it, 1, exchangeName, null, "process", "<generated>", span(0), null, error, error.message)
      }
    }

    where:
    exchangeName = "some-error-exchange"
  }

  def "test rabbit error (#command)"() {
    when:
    closure.call(channel)

    then:
    def error = thrown(exception)

    and:

    assertTraces(1) {
      trace(0, 1) {
        rabbitSpan(it, 0, null, null, operation, command, null, null, error, errorMsg)
      }
    }

    where:
    command                | exception             | errorMsg                                           | operation | closure
    "exchange.declare"     | IOException           | null                                               | null      | {
      it.exchangeDeclare("some-exchange", "invalid-type", true)
    }
    "Channel.basicConsume" | IllegalStateException | "Invalid configuration: 'queue' must be non-null." | null      | {
      it.basicConsume(null, null)
    }
    "<generated>"          | IOException           | null                                               | "receive" | {
      it.basicGet("amq.gen-invalid-channel", true)
    }
  }

  def "test spring rabbit"() {
    setup:
    def connectionFactory = new CachingConnectionFactory(connectionFactory)
    AmqpAdmin admin = new RabbitAdmin(connectionFactory)
    AmqpTemplate template = new RabbitTemplate(connectionFactory)

    def queue = new Queue("some-routing-queue", false, true, true, null)
    runWithSpan("producer parent") {
      admin.declareQueue(queue)
      template.convertAndSend(queue.name, "foo")
    }
    String message = runWithSpan("consumer parent") {
      return template.receiveAndConvert(queue.name) as String
    }

    expect:
    message == "foo"

    and:
    assertTraces(2) {
      SpanData producerSpan

      trace(0, 3) {
        span(0) {
          name "producer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, null, null, null, "queue.declare", span(0))
        rabbitSpan(it, 2, "<default>", "some-routing-queue", "send", "<default>", span(0))

        producerSpan = span(2)
      }

      trace(1, 2) {
        span(0) {
          name "consumer parent"
          hasNoParent()
        }
        rabbitSpan(it, 1, "<default>", "some-routing-queue", "receive", queue.name, span(0), producerSpan)
      }
    }
  }

  def "capture message header as span attributes"() {
    setup:
    String queueName = channel.queueDeclare().getQueue()
    def properties = new AMQP.BasicProperties.Builder().headers(["test-message-header": "test"]).build()
    channel.basicPublish("", queueName, properties, "Hello, world!".getBytes())

    def latch = new CountDownLatch(1)
    def deliveries = []

    Consumer callback = new DefaultConsumer(channel) {
      @Override
      void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties props, byte[] body) throws IOException {
        deliveries << new String(body)
        latch.countDown()
      }
    }

    channel.basicConsume(queueName, callback)
    latch.await(10, TimeUnit.SECONDS)
    expect:
    deliveries[0] == "Hello, world!"

    and:
    assertTraces(3) {
      traces.subList(1, 3).sort(orderByRootSpanKind(PRODUCER, CLIENT))
      trace(0, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.declare")
      }
      trace(1, 2) {
        rabbitSpan(it, 0, "<default>", null, "send", "<default>", null, null, null, null, false, true)
        rabbitSpan(it, 1, "<default>", null, "process", "<generated>", span(0), null, null, null, false, true)
      }
      trace(2, 1) {
        rabbitSpan(it, 0, null, null, null, "basic.consume")
      }
    }
  }

  def rabbitSpan(
    TraceAssert trace,
    int index,
    String exchange,
    String routingKey,
    String operation,
    String resource,
    SpanData parentSpan = null,
    SpanData linkSpan = null,
    Throwable exception = null,
    String errorMsg = null,
    boolean expectTimestamp = false,
    boolean testHeaders = false
  ) {

    def spanName = resource
    if (operation != null) {
      spanName = spanName + " " + operation
    }

    def rabbitCommand = trace.span(index).attributes.get(AttributeKey.stringKey("rabbitmq.command"))

    def spanKind
    switch (rabbitCommand) {
      case "basic.publish":
        spanKind = PRODUCER
        break
      case "basic.get": // fallthrough
      case "basic.deliver":
        spanKind = CONSUMER
        break
      default:
        spanKind = CLIENT
    }

    trace.span(index) {
      name spanName
      kind spanKind

      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf(parentSpan)
      }

      if (linkSpan == null) {
        hasNoLinks()
      } else {
        hasLink(linkSpan)
      }

      if (exception) {
        status ERROR
        errorEvent(exception.class, errorMsg)
      }

      attributes {
        // listener does not have access to net attributes
        if (rabbitCommand != "basic.deliver") {
          "$SemanticAttributes.NET_SOCK_PEER_ADDR" { it == "127.0.0.1" || it == "0:0:0:0:0:0:0:1" || it == null }
          "$SemanticAttributes.NET_SOCK_PEER_PORT" Long
          "$SemanticAttributes.NET_SOCK_FAMILY" { it == SemanticAttributes.NetSockFamilyValues.INET6 || it == null }
        }

        "$SemanticAttributes.MESSAGING_SYSTEM" "rabbitmq"
        "$SemanticAttributes.MESSAGING_DESTINATION_NAME" exchange
        "$SemanticAttributes.MESSAGING_DESTINATION_KIND" "queue"

        "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" { it == null || it == routingKey || it.startsWith("amq.gen-") }
        if (operation != null && operation != "send") {
          "$SemanticAttributes.MESSAGING_OPERATION" operation
        }
        if (expectTimestamp) {
          "rabbitmq.record.queue_time_ms" { it instanceof Long && it >= 0 }
        }
        if (testHeaders) {
          "messaging.header.test_message_header" { it == ["test"] }
        }

        switch (rabbitCommand) {
          case "basic.publish":
            "rabbitmq.command" "basic.publish"
            "$SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY" {
              it == null || it == "some-routing-key" || it == "some-routing-queue" || it.startsWith("amq.gen-")
            }
            "rabbitmq.delivery_mode" { it == null || it == 2 }
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            break
          case "basic.get":
            "rabbitmq.command" "basic.get"
            //TODO why this queue name is not a destination for semantic convention
            "rabbitmq.queue" { it == "some-queue" || it == "some-routing-queue" || it.startsWith("amq.gen-") }
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" { it == null || it instanceof Long }
            break
          case "basic.deliver":
            "rabbitmq.command" "basic.deliver"
            "$SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES" Long
            break
          default:
            "rabbitmq.command" { it == null || it == resource }
        }
      }
    }
  }
}
