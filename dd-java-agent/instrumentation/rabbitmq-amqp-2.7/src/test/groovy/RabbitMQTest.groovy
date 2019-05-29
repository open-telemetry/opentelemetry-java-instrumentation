import com.rabbitmq.client.AMQP
import com.rabbitmq.client.AlreadyClosedException
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.GetResponse
import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
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
import java.util.concurrent.Phaser

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

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
    } catch (AlreadyClosedException e) {
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
    assertTraces(2) {
      trace(0, 1) {
        rabbitSpan(it, "basic.get <generated>", true, TEST_WRITER[1][1])
      }
      trace(1, 5) {
        span(0) {
          operationName "parent"
        }
        // reverse order
        rabbitSpan(it, 1, "basic.publish $exchangeName -> $routingKey", false, span(0))
        rabbitSpan(it, 2, "queue.bind", false, span(0))
        rabbitSpan(it, 3, "queue.declare", false, span(0))
        rabbitSpan(it, 4, "exchange.declare", false, span(0))
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
        rabbitSpan(it, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, "basic.publish <default> -> <generated>")
      }
      trace(2, 1) {
        rabbitSpan(it, "basic.get <generated>", true, TEST_WRITER[1][0])
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

    def phaser = new Phaser()
    phaser.register()
    phaser.register()
    def deliveries = []

    Consumer callback = new DefaultConsumer(channel) {
      @Override
      void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        phaser.arriveAndAwaitAdvance() // Ensure publish spans are reported first.
        deliveries << new String(body)
      }
    }

    channel.basicConsume(queueName, callback)

    (1..messageCount).each {
      TEST_WRITER.waitForTraces(2 + (it * 2))
      channel.basicPublish(exchangeName, "", null, "msg $it".getBytes())
      TEST_WRITER.waitForTraces(3 + (it * 2))
      phaser.arriveAndAwaitAdvance()
    }
    def resource = messageCount % 2 == 0 ? "basic.deliver <generated>" : "basic.deliver $queueName"

    expect:
    assertTraces(4 + (messageCount * 2)) {
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
        def publishSpan = null
        trace(2 + (it * 2), 1) {
          publishSpan = span(0)
          rabbitSpan(it, "basic.publish $exchangeName -> <all>")
        }
        trace(3 + (it * 2), 1) {
          rabbitSpan(it, resource, true, publishSpan)
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

    def phaser = new Phaser()
    phaser.register()
    phaser.register()

    Consumer callback = new DefaultConsumer(channel) {
      @Override
      void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        phaser.arriveAndAwaitAdvance() // Ensure publish spans are reported first.
        throw error
        // Unfortunately this doesn't seem to be observable in the test outside of the span generated.
      }
    }

    channel.basicConsume(queueName, callback)

    TEST_WRITER.waitForTraces(2)
    channel.basicPublish(exchangeName, "", null, "msg".getBytes())
    TEST_WRITER.waitForTraces(3)
    phaser.arriveAndAwaitAdvance()

    expect:
    assertTraces(6) {
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
      def publishSpan = null
      trace(4, 1) {
        publishSpan = span(0)
        rabbitSpan(it, "basic.publish $exchangeName -> <all>")
      }
      trace(5, 1) {
        rabbitSpan(it, "basic.deliver <generated>", true, publishSpan, error, error.message)
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
    assertTraces(3) {
      trace(0, 1) {
        rabbitSpan(it, "queue.declare")
      }
      trace(1, 1) {
        rabbitSpan(it, "basic.publish <default> -> some-routing-queue")
      }
      trace(2, 1) {
        rabbitSpan(it, "basic.get $queue.name", true, TEST_WRITER[1][0])
      }
    }
  }

  def rabbitSpan(
    TraceAssert trace,
    String resource,
    Boolean distributedRootSpan = false,
    DDSpan parentSpan = null,
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
    DDSpan parentSpan = null,
    Throwable exception = null,
    String errorMsg = null
  ) {
    trace.span(index) {
      serviceName "rabbitmq"
      operationName "amqp.command"
      resourceName resource
      switch (span.tags["amqp.command"]) {
        case "basic.publish":
          spanType DDSpanTypes.MESSAGE_PRODUCER
          break
        case "basic.get":
        case "basic.deliver":
          spanType DDSpanTypes.MESSAGE_CONSUMER
          break
        default:
          spanType DDSpanTypes.MESSAGE_CLIENT
      }

      if (parentSpan) {
        childOf parentSpan
      } else {
        parent()
      }

      errored exception != null

      tags {
        if (exception) {
          errorTags(exception.class, errorMsg)
        }
        "$Tags.COMPONENT.key" "rabbitmq-amqp"
        "$Tags.PEER_HOSTNAME.key" { it == null || it instanceof String }
        "$Tags.PEER_HOST_IPV4.key" { "127.0.0.1" }
        "$Tags.PEER_PORT.key" { it == null || it instanceof Integer }

        switch (tag("amqp.command")) {
          case "basic.publish":
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_PRODUCER
            "amqp.command" "basic.publish"
            "amqp.exchange" { it == null || it == "some-exchange" || it == "some-error-exchange" }
            "amqp.routing_key" {
              it == null || it == "some-routing-key" || it == "some-routing-queue" || it.startsWith("amq.gen-")
            }
            "amqp.delivery_mode" { it == null || it == 2 }
            "message.size" Integer
            break
          case "basic.get":
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CONSUMER
            "amqp.command" "basic.get"
            "amqp.queue" { it == "some-queue" || it == "some-routing-queue" || it.startsWith("amq.gen-") }
            "message.size" { it == null || it instanceof Integer }
            break
          case "basic.deliver":
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CONSUMER
            "amqp.command" "basic.deliver"
            "span.origin.type" { it == "RabbitMQTest\$1" || it == "RabbitMQTest\$2" }
            "amqp.exchange" { it == "some-exchange" || it == "some-error-exchange" }
            "message.size" Integer
            break
          default:
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "amqp.command" { it == null || it == resource }
        }
        defaultTags(distributedRootSpan)
      }
    }
  }
}
