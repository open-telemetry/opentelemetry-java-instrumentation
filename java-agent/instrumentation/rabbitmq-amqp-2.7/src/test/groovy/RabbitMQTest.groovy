import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AlreadyClosedException
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.ShutdownSignalException
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.SpanData
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.AmqpTemplate
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.containers.GenericContainer
import spock.lang.Requires
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

// Do not run tests locally on Java7 since testcontainers are not compatible with Java7
// It is fine to run on CI because CI provides rabbitmq externally, not through testcontainers
@Requires({ "true" == System.getenv("CI") || jvm.java8Compatible })
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
      CI will provide us with rabbitmq container running along side our build.
      When building locally, however, we need to take matters into our own hands
      and we use 'testcontainers' for this.
     */
    if ("true" != System.getenv("CI")) {
      rabbbitMQContainer = new GenericContainer('rabbitmq:latest')
        .withExposedPorts(defaultRabbitMQPort)
        .withStartupTimeout(Duration.ofSeconds(120))
//        .withLogConsumer { output ->
//        print output.utf8String
//      }
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
    } catch (AlreadyClosedException | ShutdownSignalException e) {
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
          tags {
          }
        }
        rabbitSpan(it, 1, "exchange.declare", false, span(0))
        rabbitSpan(it, 2, "queue.declare", false, span(0))
        rabbitSpan(it, 3, "queue.bind", false, span(0))
        rabbitSpan(it, 4, "basic.publish $exchangeName -> $routingKey", false, span(0))
        rabbitSpan(it, 5, "basic.get <generated>", true, span(4))
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
    assertTraces(2) {
      trace(0, 1) {
        rabbitSpan(it, 0, "queue.declare")
      }
      trace(1, 2) {
        rabbitSpan(it, 0, "basic.publish <default> -> <generated>")
        rabbitSpan(it, 1, "basic.get <generated>", true, span(0))
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
      channel.basicPublish(exchangeName, "", null, "msg $it".getBytes())
    }
    def resource = messageCount % 2 == 0 ? "basic.deliver <generated>" : "basic.deliver $queueName"

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
          rabbitSpan(it, 0, "basic.publish $exchangeName -> <all>")
          rabbitSpan(it, 1, resource, true, span(0))
        }
      }
    }

    deliveries == (1..messageCount).collect { "msg $it" }

    where:
    exchangeName = "some-exchange"
    messageCount << (1..4)
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
        rabbitSpan(it, 0, "basic.publish $exchangeName -> <all>")
        rabbitSpan(it, 1, "basic.deliver <generated>", true, span(0), error, error.message)
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
        rabbitSpan(it, command, false, null, throwable, errorMsg)
      }
    }

    where:
    command                 | exception             | errorMsg                                           | closure
    "exchange.declare"      | IOException           | null                                               | {
      it.exchangeDeclare("some-exchange", "invalid-type", true)
    }
    "Channel.basicConsume"  | IllegalStateException | "Invalid configuration: 'queue' must be non-null." | {
      it.basicConsume(null, null)
    }
    "basic.get <generated>" | IOException           | null                                               | {
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
    assertTraces(2) {
      trace(0, 1) {
        rabbitSpan(it, "queue.declare")
      }
      trace(1, 2) {
        rabbitSpan(it, 0, "basic.publish <default> -> some-routing-queue")
        rabbitSpan(it, 1, "basic.get $queue.name", true, span(0))
      }
    }
  }

  def rabbitSpan(
    TraceAssert trace,
    String resource,
    Boolean distributedRootSpan = false,
    Object parentSpan = null,
    Throwable exception = null,
    String errorMsg = null
  ) {
    rabbitSpan(trace, 0, resource, distributedRootSpan, parentSpan, exception, errorMsg)
  }

  def rabbitSpan(
    TraceAssert trace,
    int index,
    String resource,
    Boolean distributedRootSpan = false,
    Object parentSpan = null,
    Throwable exception = null,
    String errorMsg = null
  ) {
    trace.span(index) {
      operationName "amqp.command"

      if (parentSpan) {
        childOf((SpanData) parentSpan)
      } else {
        parent()
      }

      errored exception != null

      tags {
        "$MoreTags.SERVICE_NAME" "rabbitmq"
        "$MoreTags.RESOURCE_NAME" resource
        "$Tags.COMPONENT" "rabbitmq-amqp"
        "$Tags.PEER_HOSTNAME" { it == null || it instanceof String }
        "$Tags.PEER_HOST_IPV4" { "127.0.0.1" }
        "$Tags.PEER_PORT" { it == null || it instanceof Long }

        switch (tag("amqp.command")?.stringValue) {
          case "basic.publish":
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_PRODUCER
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_PRODUCER
            "amqp.command" "basic.publish"
            "amqp.exchange" { it == null || it == "some-exchange" || it == "some-error-exchange" }
            "amqp.routing_key" {
              it == null || it == "some-routing-key" || it == "some-routing-queue" || it.startsWith("amq.gen-")
            }
            "amqp.delivery_mode" { it == null || it == 2 }
            "message.size" Long
            break
          case "basic.get":
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "amqp.command" "basic.get"
            "amqp.queue" { it == "some-queue" || it == "some-routing-queue" || it.startsWith("amq.gen-") }
            "message.size" { it == null || it instanceof Long }
            break
          case "basic.deliver":
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CONSUMER
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CONSUMER
            "amqp.command" "basic.deliver"
            "span.origin.type" { it == "RabbitMQTest\$1" || it == "RabbitMQTest\$2" }
            "amqp.exchange" { it == "some-exchange" || it == "some-error-exchange" }
            "message.size" Long
            break
          default:
            "$MoreTags.SPAN_TYPE" SpanTypes.MESSAGE_CLIENT
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "amqp.command" { it == null || it == resource }
        }
        if (exception) {
          errorTags(exception.class, errorMsg)
        }
      }
    }
  }
}
