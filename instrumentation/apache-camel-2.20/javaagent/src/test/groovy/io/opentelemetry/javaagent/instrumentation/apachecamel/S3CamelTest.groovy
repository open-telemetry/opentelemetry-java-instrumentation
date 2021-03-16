/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.api.trace.SpanKind.INTERNAL

import com.amazonaws.services.sqs.model.ReceiveMessageResult
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.SpringApplication
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Ignore
import spock.lang.Shared
@Ignore("Does not work with localstack - X-Ray features needed")
class S3CamelTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector

  def setupSpec() {
    awsConnector = AwsConnector.liveAws()
  }

  def startCamelApp(String bucketName, String queueName) {
    def app = new SpringApplication(S3Config)
    app.setDefaultProperties(ImmutableMap.of("bucketName", bucketName, "queueName", queueName))
    return app.run()
  }

  def "camel S3 producer - camel SQS consumer"() {
    setup:
    String bucketName = "bucket-test-s3-sqs-camel"
    String queueName = "s3SqsCamelTest"

    // setup infra
    String queueUrl = awsConnector.createQueue(queueName)
    awsConnector.createBucket(bucketName)

    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn)

    // consume test message from AWS
    ReceiveMessageResult rmr = awsConnector.receiveMessage(queueUrl)
    println("MESSAGES: "+rmr)

    // wait for setup traces
    waitAndClearSetupTraces(queueUrl, queueName, bucketName)

    when:
    def applicationContext = startCamelApp(bucketName, queueName)
    def camelContext = applicationContext.getBean(CamelContext)
    ProducerTemplate template = camelContext.createProducerTemplate()
    template.sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(6) {
      trace(0, 1) {
        span(0) {
          name "SQS.ListQueues"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ListQueues"
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "S3.ListObjects"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ListObjects"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "GET"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(2, 5) {
        span(0) {
          name "input"
          kind INTERNAL
          hasNoParent()
          attributes {
            "apache-camel.uri" "direct://input"
          }
        }
        span(1) {
          name "aws-s3"
          kind INTERNAL
          childOf span(0)
          attributes {
            "apache-camel.uri" "aws-s3://${bucketName}?amazonS3Client=%23s3Client"
          }
        }
        span(2) {
          name "S3.PutObject"
          kind CLIENT
          childOf span(1)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "PutObject"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
        span(3) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(2)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
        span(4) {
          name queueName
          kind INTERNAL
          childOf span(2)
          attributes {
            "apache-camel.uri" "aws-sqs://${queueName}?amazonSQSClient=%23sqsClient&delay=1000"
            "messaging.destination" queueName
            "messaging.message_id" String
          }
        }
      }
      // HTTP "client" receiver span, one per each SQS request
      trace(3, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
      }
      // camel polling
      trace(4, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
      }
      // camel cleaning received msg
      trace(5, 1) {
        span(0) {
          name "SQS.DeleteMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
      }
    }

    cleanup:
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)
  }

  def waitAndClearSetupTraces(queueUrl, queueName, bucketName) {
    assertTraces(7) {
      trace(0, 1) {
        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateQueue"
            "aws.queue.name" queueName
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.peer.port" { it == null || Number }
            "net.transport" "IP.TCP"
          }
        }
      }
      trace(1, 1) {
        span(0) {
          name "S3.CreateBucket"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateBucket"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "SQS.GetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(3, 1) {
        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(4, 1) {
        span(0) {
          name "S3.SetBucketNotificationConfiguration"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetBucketNotificationConfiguration"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(5, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
      // http receive span
      trace(6, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" { it == null || Number }
          }
        }
      }
    }
    clearExportedData()
  }
}