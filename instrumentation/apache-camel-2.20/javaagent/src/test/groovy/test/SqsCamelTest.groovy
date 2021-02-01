/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.api.trace.Span.Kind.CONSUMER
import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.api.trace.Span.Kind.PRODUCER

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Shared

class SqsCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server

  @Shared
  def sqs
  @Shared
  int sqsPort

  def setupSpec() {

    /**
     * Temporarily using emq instead of localstack till the latter supports AWS trace propagation
     *
    sqs = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
      .withServices(LocalStackContainer.Service.SQS)
    sqs.start()
    sqsPort = sqs.getMappedPort(4566)

    def app = new SpringApplication(SqsConfig)
    app.addInitializers(new ApplicationContextInitializer<AbstractApplicationContext>() {
      @Override
      void initialize(AbstractApplicationContext applicationContext) {
        applicationContext.getBeanFactory().registerSingleton("localStack", sqs)
      }
    })
    server = app.run()**/

    sqsPort = PortUtils.randomOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

    def app = new SpringApplication(SqsConfig)
    app.setDefaultProperties(ImmutableMap.of("sqs.port", sqsPort))
    server = app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
    if (sqs != null) {
      sqs.stopAndWait()
    }
  }

  def "simple sqs producer-consumer services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(6) {
      trace(0, 5) {

        span(0) {
          name "input"
          kind INTERNAL
          hasNoParent()
          attributes {
            "apache-camel.uri" "direct://input"
          }
        }
        span(1) {
          name "sqsCamelTest"
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sqs://sqsCamelTest?amazonSQSClient=%23sqsClient"
            "messaging.destination" "sqsCamelTest"
          }
        }
        span(2) {
          name "SQS.SendMessage"
          kind PRODUCER
          childOf span(1)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "SendMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
        span(3) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(2)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "http.user_agent" String
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
        span(4) {
          name "sqsCamelTest"
          kind INTERNAL
          childOf span(1)
          attributes {
            "apache-camel.uri" "aws-sqs://sqsCamelTest?amazonSQSClient=%23sqsClient&messageAttributeNames=traceparent"
            "messaging.destination" "sqsCamelTest"
            "messaging.message_id" String
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "DeleteMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(3, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(4, 1) {
        it.span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(5, 1) {
        it.span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" "http://localhost:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/queue/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://localhost:$sqsPort"
            "net.peer.name" "localhost"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
    }
  }
}
