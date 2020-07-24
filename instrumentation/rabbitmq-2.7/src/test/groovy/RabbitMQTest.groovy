/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.ShutdownSignalException
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.trace.attributes.SemanticAttributes
import java.time.Duration
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.containers.GenericContainer
import spock.lang.Requires
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static io.opentelemetry.trace.Span.Kind.CLIENT
import static io.opentelemetry.trace.Span.Kind.CONSUMER
import static io.opentelemetry.trace.Span.Kind.PRODUCER

// Do not run tests on Java7 since testcontainers are not compatible with Java7
@Requires({ jvm.java8Compatible })
class RabbitMQTest extends AgentTestRunner {

  /*
    Note: type here has to stay undefined, otherwise tests will fail in CI in Java 7 because
    'testcontainers' are built for Java 8 and Java 7 cannot load this class.
   */
  @Shared
  def rabbbitMQContainer
  @Shared
  def defaultRabbitMQPort = 5672
  @Shared
  InetSocketAddress rabbitmqAddress = new InetSocketAddress("127.0.0.1", defaultRabbitMQPort)

  ConnectionFactory factory = new ConnectionFactory(host: rabbitmqAddress.hostName, port: rabbitmqAddress.port)
  Connection conn = factory.newConnection()
  Channel channel = conn.createChannel()

  def setupSpec() {

    /*
      CircleCI will provide us with rabbitmq container running along side our build.
      When building locally and in GitHub actions, however, we need to take matters into our own hands
      and we use 'testcontainers' for this.
     */
    if ("true" != System.getenv("CIRCLECI")) {
      rabbbitMQContainer = new GenericContainer('rabbitmq:latest')
        .withExposedPorts(defaultRabbitMQPort)
        .withStartupTimeout(Duration.ofSeconds(120))
      rabbbitMQContainer.start()
      rabbitmqAddress = new InetSocketAddress(
        rabbbitMQContainer.containerIpAddress,
        rabbbitMQContainer.getMappedPort(defaultRabbitMQPort)
      )
    }
  }

  def cleanupSpec() {
    if (rabbbitMQContainer) {
      rabbbitMQContainer.stop()
    }
  }

  def cleanup() {
    try {
      channel.close()
      conn.close()
    } catch (ShutdownSignalException ignored) {
      // Ignore
    }
  }

  def "test rabbit publish/get"() {
    setup:
    GetResponse response = runUnderTrace("parent") {
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
          operationName "parent"
          attributes {
          }
        }
        rabbitSpan(it, 1, "exchange.declare", span(0))
        rabbitSpan(it, 2, "queue.declare", span(0))
        rabbitSpan(it, 3, "queue.bind", span(0))
        rabbitSpan(it, 4, "$exchangeName -> $routingKey", span(0))
        rabbitSpan(it, 5, "<generated>", span(0), span(4))
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
      trace(0, 1) {
        rabbitSpan(it, 0, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, "<default> -> <generated>")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, "<generated>", null, traces[1][0])
      }
    }
  }

  def "test rabbit consume #messageCount messages"() {
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
        rabbitSpan(it, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, "basic.consume")
      }
      (1..messageCount).each {
        trace(3 + it, 2) {
          rabbitSpan(it, 0, "$exchangeName -> <all>")
          rabbitSpan(it, 1, resource, span(0), null, null, null, setTimestamp)
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
        rabbitSpan(it, "exchange.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, "queue.declare")
      }
      trace(2, 1) {
        rabbitSpan(it, "queue.bind")
      }
      trace(3, 1) {
        rabbitSpan(it, "basic.consume")
      }
      trace(4, 2) {
        rabbitSpan(it, 0, "$exchangeName -> <all>")
        rabbitSpan(it, 1, "<generated>", span(0), null, error, error.message)
      }
    }

    where:
    exchangeName = "some-error-exchange"
  }

  def "test rabbit error (#command)"() {
    when:
    closure.call(channel)

    then:
    def throwable = thrown(exception)

    and:

    assertTraces(1) {
      trace(0, 1) {
        rabbitSpan(it, command, null, null, throwable, errorMsg)
      }
    }

    where:
    command                | exception             | errorMsg                                           | closure
    "exchange.declare"     | IOException           | null                                               | {
      it.exchangeDeclare("some-exchange", "invalid-type", true)
    }
    "Channel.basicConsume" | IllegalStateException | "Invalid configuration: 'queue' must be non-null." | {
      it.basicConsume(null, null)
    }
    "<generated>"          | IOException           | null                                               | {
      it.basicGet("amq.gen-invalid-channel", true)
    }
  }

  def "test spring rabbit"() {
    setup:
    def connectionFactory = new CachingConnectionFactory(rabbitmqAddress.hostName, rabbitmqAddress.port)
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
      trace(0, 1) {
        rabbitSpan(it, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, 0, "<default> -> some-routing-queue")
      }
      trace(2, 1) {
        rabbitSpan(it, 0, queue.name, null, traces[1][0])
      }
    }
  }

  def rabbitSpan(
    TraceAssert trace,
    String resource,
    Object parentSpan = null,
    Object linkSpan = null,
    Throwable exception = null,
    String errorMsg = null,
    Boolean expectTimestamp = false
  ) {
    rabbitSpan(trace, 0, resource, parentSpan, linkSpan, exception, errorMsg, expectTimestamp)
  }

  def rabbitSpan(
    TraceAssert trace,
    int index,
    String resource,
    Object parentSpan = null,
    Object linkSpan = null,
    Throwable exception = null,
    String errorMsg = null,
    Boolean expectTimestamp = false
  ) {
    trace.span(index) {
      operationName resource

      switch (trace.span(index).attributes.get("amqp.command")?.stringValue) {
        case "basic.publish":
          spanKind PRODUCER
          break
        case "basic.get":
          spanKind CLIENT
          break
        case "basic.deliver":
          spanKind CONSUMER
          break
        default:
          spanKind CLIENT
      }

      if (parentSpan) {
        childOf((SpanData) parentSpan)
      } else {
        parent()
      }

      if (linkSpan) {
        hasLink((SpanData) linkSpan)
      }

      errored exception != null

      attributes {
        "${SemanticAttributes.NET_PEER_NAME.key()}" { it == null || it instanceof String }
        "${SemanticAttributes.NET_PEER_IP.key()}" { "127.0.0.1" }
        "${SemanticAttributes.NET_PEER_PORT.key()}" { it == null || it instanceof Long }
        if (expectTimestamp) {
          "record.queue_time_ms" { it instanceof Long && it >= 0 }
        }

        switch (attribute("amqp.command")?.stringValue) {
          case "basic.publish":
            "amqp.command" "basic.publish"
            "amqp.exchange" { it == null || it == "some-exchange" || it == "some-error-exchange" }
            "amqp.routing_key" {
              it == null || it == "some-routing-key" || it == "some-routing-queue" || it.startsWith("amq.gen-")
            }
            "amqp.delivery_mode" { it == null || it == 2 }
            "message.size" Long
            break
          case "basic.get":
            "amqp.command" "basic.get"
            "amqp.queue" { it == "some-queue" || it == "some-routing-queue" || it.startsWith("amq.gen-") }
            "message.size" { it == null || it instanceof Long }
            break
          case "basic.deliver":
            "amqp.command" "basic.deliver"
            "amqp.exchange" { it == "some-exchange" || it == "some-error-exchange" }
            "message.size" Long
            break
          default:
            "amqp.command" { it == null || it == resource }
        }
        if (exception) {
          errorAttributes(exception.class, errorMsg)
        }
      }
    }
  }
}
