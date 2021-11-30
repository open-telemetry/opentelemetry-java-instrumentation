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
    GetResponse response = runWithSpan("parent") {
      channel.exchangeDeclare(exchangeName, "direct", false)
      String queueName = channel.queueDeclare().getQueue()
      channel.queueBind(queueName, exchangeName, routingKey)
      channel.basicPublish(exchangeName, routingKey, null, "Hello, world!".getBytes())
      return channel.basicGet(queueName, true)
    }

    expect:
    new String(response.getBody()) == "Hello, world!"

    and:
    assertTraces(1) {
      trace(0, 6) {
        span(0) {
          name "parent"
          attributes {
          }
        }
        rabbitSpan(it, 1, null, null, null, "exchange.declare", span(0))
        rabbitSpan(it, 2, null, null, null, "queue.declare", span(0))
        rabbitSpan(it, 3, null, null, null, "queue.bind", span(0))
        rabbitSpan(it, 4, exchangeName, routingKey, "send", "$exchangeName", span(0))
        rabbitSpan(it, 5, exchangeName, routingKey, "receive", "<generated>", span(0))
      }
    }

    where:
    exchangeName    | routingKey
    "some-exchange" | "some-routing-key"
  }

  def "test rabbit publish/get default exchange"() {
    setup:
    String queueName = channel.queueDeclare().getQueue()
    channel.basicPublish("", queueName, null, "Hello, world!".getBytes())
    GetResponse response = channel.basicGet(queueName, true)

    expect:
    new String(response.getBody()) == "Hello, world!"

    and:
    assertTraces(3) {
      traces.subList(1, 3).sort(orderByRootSpanKind(PRODUCER, CLIENT))
      trace(0, 1) {
        rabbitSpan(it, 0, null, null, null, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, "<default>", null, "send", "<default>")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, "<default>", null, "receive", "<generated>", null)
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
        rabbitSpan(it, null, null, null, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, null, null, null, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, null, null, null, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, null, null, null, "basic.consume")
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
        rabbitSpan(it, null, null, null, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, null, null, null, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, null, null, null, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, null, null, null, "basic.consume")
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
        rabbitSpan(it, null, null, operation, command, null, null, error, errorMsg)
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
    def queue = new Queue("some-routing-queue", false, true, true, null)
    admin.declareQueue(queue)
    AmqpTemplate template = new RabbitTemplate(connectionFactory)
    template.convertAndSend(queue.name, "foo")
    String message = (String) template.receiveAndConvert(queue.name)

    expect:
    message == "foo"

    and:
    assertTraces(3) {
      traces.subList(1, 3).sort(orderByRootSpanKind(PRODUCER, CLIENT))
      trace(0, 1) {
        rabbitSpan(it, null, null, null, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, "<default>", "some-routing-queue", "send", "<default>")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, "<default>", "some-routing-queue", "receive", queue.name, null)
      }
    }
  }

  def rabbitSpan(
    TraceAssert trace,
    String exchange,
    String routingKey,
    String operation,
    String resource,
    Object parentSpan = null,
    Object linkSpan = null,
    Throwable exception = null,
    String errorMsg = null,
    Boolean expectTimestamp = false
  ) {
    rabbitSpan(trace, 0, exchange, routingKey, operation, resource, parentSpan, linkSpan, exception, errorMsg, expectTimestamp)
  }

  def rabbitSpan(
    TraceAssert trace,
    int index,
    String exchange,
    String routingKey,
    String operation,
    String resource,
    Object parentSpan = null,
    Object linkSpan = null,
    Throwable exception = null,
    String errorMsg = null,
    Boolean expectTimestamp = false
  ) {

    def spanName = resource
    if (operation != null) {
      spanName = spanName + " " + operation
    }

    trace.span(index) {
      name spanName

      switch (trace.span(index).attributes.get(AttributeKey.stringKey("rabbitmq.command"))) {
        case "basic.publish":
          kind PRODUCER
          break
        case "basic.get":
          kind CLIENT
          break
        case "basic.deliver":
          kind CONSUMER
          break
        default:
          kind CLIENT
      }

      if (parentSpan) {
        childOf((SpanData) parentSpan)
      } else {
        hasNoParent()
      }

      if (linkSpan) {
        hasLink((SpanData) linkSpan)
      }

      if (exception) {
        status ERROR
        errorEvent(exception.class, errorMsg)
      }

      attributes {
        "${SemanticAttributes.NET_PEER_NAME.key}" { it == null || it instanceof String }
        "${SemanticAttributes.NET_PEER_IP.key}" { it == null || isContainerIpAddress(it) }
        "${SemanticAttributes.NET_PEER_PORT.key}" { it == null || it instanceof Long }

        "${SemanticAttributes.MESSAGING_SYSTEM.key}" "rabbitmq"
        "${SemanticAttributes.MESSAGING_DESTINATION.key}" exchange
        "${SemanticAttributes.MESSAGING_DESTINATION_KIND.key}" "queue"

        "${SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY}" { it == null || it == routingKey || it.startsWith("amq.gen-") }
        if (operation != null && operation != "send") {
          "${SemanticAttributes.MESSAGING_OPERATION.key}" operation
        }
        if (expectTimestamp) {
          "rabbitmq.record.queue_time_ms" { it instanceof Long && it >= 0 }
        }

        switch (trace.span(index).attributes.get(AttributeKey.stringKey("rabbitmq.command"))) {
          case "basic.publish":
            "rabbitmq.command" "basic.publish"
            "${SemanticAttributes.MESSAGING_RABBITMQ_ROUTING_KEY}" {
              it == null || it == "some-routing-key" || it == "some-routing-queue" || it.startsWith("amq.gen-")
            }
            "rabbitmq.delivery_mode" { it == null || it == 2 }
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            break
          case "basic.get":
            "rabbitmq.command" "basic.get"
            //TODO why this queue name is not a destination for semantic convention
            "rabbitmq.queue" { it == "some-queue" || it == "some-routing-queue" || it.startsWith("amq.gen-") }
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" { it == null || it instanceof Long }
            break
          case "basic.deliver":
            "rabbitmq.command" "basic.deliver"
            "${SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES.key}" Long
            break
          default:
            "rabbitmq.command" { it == null || it == resource }
        }
      }
    }
  }
}
