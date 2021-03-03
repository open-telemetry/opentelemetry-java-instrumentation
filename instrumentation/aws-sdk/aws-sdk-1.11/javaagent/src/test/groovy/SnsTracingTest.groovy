/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER

import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.CreateTopicResult
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.CreateQueueResult
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import spock.lang.Ignore
import spock.lang.Shared

class SnsTracingTest extends AgentInstrumentationSpecification {

  @Shared
  int sqsPort
  @Shared
  LocalStackContainer localstack
  @Shared
  AmazonSQSAsyncClient sqsClient

  @Shared
  int snsPort
  @Shared
  LocalStackContainer sns
  @Shared
  AmazonSNSAsyncClient snsClient

  def setupSpec() {

    localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      .withEnv("DEBUG", "1")
      .withEnv("SQS_PROVIDER", "elasticmq")
    localstack.start()

    sqsPort = localstack.getMappedPort(4566)

    sqsClient = AmazonSQSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()

    snsPort = localstack.getMappedPort(4566)
    snsClient = AmazonSNSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SNS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      .build()

    localstack.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("test")))
  }

  def cleanupSpec() {
    if (localstack != null) {
      localstack.stop()
    }
    if (sns != null) {
      sns.stop()
    }
  }

  def createQueue(String queueName) {
    CreateQueueResult cqr = sqsClient.createQueue(queueName)
    sqsClient.setQueueAttributes(cqr.getQueueUrl(), Collections.singletonMap("Policy", policy(queueName)))
    return cqr.getQueueUrl()
  }

  def getQueueArn(String queueUrl) {
    return sqsClient.getQueueAttributes(
      new GetQueueAttributesRequest(queueUrl)
        .withAttributeNames("QueueArn")).getAttributes()
      .get("QueueArn")
  }

  def createAndSubscribeTopic(String topicName, String queueArn) {
    CreateTopicResult ctr = snsClient.createTopic(topicName)
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn)
    return ctr.getTopicArn()
  }

  @Ignore("Requires https://github.com/localstack/localstack/issues/3669 to work with localstack")
  def "simple SNS producer - SQS consumer services"() {
    setup:
    String queueName = "snsToSqsTestQueue"
    String topicName = "snsToSqsTestTopic"

    String queueUrl = createQueue(queueName)
    String queueArn = getQueueArn(queueUrl)
    String topicArn = createAndSubscribeTopic(topicName, queueArn)

    when:
    snsClient.publish(topicArn, "Hello There")
    Thread.sleep(1000)
    ReceiveMessageRequest rmr = new ReceiveMessageRequest(queueUrl).withMessageAttributeNames("test")
    sqsClient.receiveMessage(rmr)
    Thread.sleep(1000)

    then:
    assertTraces(7) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateQueueRequest"
            "aws.queue.name" queueName
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(1, 1) {

        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SetQueueAttributesRequest"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
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
            "aws.operation" "GetQueueAttributesRequest"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(3, 1) {

        span(0) {
          name "SNS.CreateTopic"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "CreateTopicRequest"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(4, 1) {

        span(0) {
          name "SNS.Subscribe"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "SubscribeRequest"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(5, 2) {
        span(0) {
          name "SNS.Publish"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "PublishRequest"
            "aws.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(6, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "ReceiveMessageRequest"
            "aws.queue.url" queueUrl
            "aws.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
    }
  }

  def policy(String queueName) {
    return String.format(SQS_POLICY, queueName)
  }

  private static final String SQS_POLICY = "{" +
    "  \"Statement\": [" +
    "    {" +
    "      \"Effect\": \"Allow\"," +
    "      \"Principal\": \"*\"," +
    "      \"Action\": \"sqs:SendMessage\"," +
    "      \"Resource\": \"arn:aws:sqs:us-east-1:906383545488:%s\"" +
    "    }]" +
    "}"
}