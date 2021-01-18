/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import static io.opentelemetry.api.trace.Span.Kind.CLIENT
import static io.opentelemetry.api.trace.Span.Kind.CONSUMER
import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.api.trace.Span.Kind.PRODUCER

import com.google.common.collect.ImmutableMap
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.PortUtils
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.elasticmq.rest.sqs.SQSRestServer
import org.elasticmq.rest.sqs.SQSRestServerBuilder
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Shared

class SqsCamelTest extends AgentTestRunner {

  @Shared
  ConfigurableApplicationContext server

  @Shared
  SQSRestServer sqs
  @Shared
  int sqsPort

  def setupSpec() {
    sqsPort = PortUtils.randomOpenPort()
    sqs = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start()
    println getClass().name + " SQS server started at: localhost:$sqsPort/"

    def app = new SpringApplication(SqsConfig)
    app.setDefaultProperties(["sqs.port": sqsPort]))
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
    }
  }
}
