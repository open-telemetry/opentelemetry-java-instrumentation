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
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.AbstractApplicationContext
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

class SqsCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server

  @Shared
  def sqs
  @Shared
  int sqsPort

  def setupSpec() {

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
    server = app.run()
  }

  def cleanupSpec() {
    if (server != null) {
      server.close()
      server = null
    }
    if (sqs != null) {
      sqs.stop()
    }
  }

  def "simple sqs producer-consumer services"() {
    setup:
    def camelContext = server.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()

    when:
    template.sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(5) {
      trace(0, 3) {

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
          kind PRODUCER
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-sqs://sqsCamelTest?amazonSQSClient=%23sqsClient"
            "messaging.destination" "sqsCamelTest"
          }
        }
        span(2) {
          name "sqsCamelTest"
          kind CONSUMER
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
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
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
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "DeleteMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
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
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
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
            "aws.endpoint" "http://127.0.0.1:$sqsPort"
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" "http://localhost:$sqsPort/000000000000/sqsCamelTest"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" "http://127.0.0.1:$sqsPort"
            "net.peer.name" "127.0.0.1"
            "net.peer.port" sqsPort
            "net.transport" "IP.TCP"
          }
        }
      }
    }
  }
}
<<<<<<< HEAD
=======

>>>>>>> 3deda3e12 (adding AWS SQS tests to Apache Camel instrumentation)
