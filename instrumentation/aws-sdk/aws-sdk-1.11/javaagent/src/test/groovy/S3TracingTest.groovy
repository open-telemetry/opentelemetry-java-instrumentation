/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.BucketNotificationConfiguration
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.QueueConfiguration
import com.amazonaws.services.s3.model.S3Event
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.slf4j.LoggerFactory
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import spock.lang.Ignore
import spock.lang.Shared

class S3TracingTest extends AgentInstrumentationSpecification {

  @Shared
  LocalStackContainer localstack
  @Shared
  AmazonSQSAsyncClient sqsClient
  @Shared
  AmazonS3Client s3Client

  def setupSpec() {

    localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      .withEnv("DEBUG", "1")
      .withEnv("SQS_PROVIDER", "elasticmq")
    localstack.start()

    sqsClient = AmazonSQSAsyncClient.asyncBuilder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SQS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      //.withRegion(Regions.US_EAST_1)
      .build()

    s3Client = AmazonS3Client.builder()
      .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SNS))
      .withCredentials(localstack.getDefaultCredentialsProvider())
      //.withRegion(Regions.US_EAST_1)
      .build()

    localstack.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("test")))
  }

  def cleanupSpec() {
    if (localstack != null) {
      localstack.stop()
    }
  }

  def createQueue(String queueName) {
    return sqsClient.createQueue(queueName).getQueueUrl()
  }

  def getQueueArn(String queueUrl) {
    return sqsClient.getQueueAttributes(
      new GetQueueAttributesRequest(queueUrl)
        .withAttributeNames("QueueArn")).getAttributes()
      .get("QueueArn")
  }

  def setQueuePolicy(String queueUrl, String queueArn) {
    sqsClient.setQueueAttributes(queueUrl, Collections.singletonMap("Policy", policy(queueArn)))
  }

  def createBucket(String bucketName) {
    s3Client.createBucket(bucketName)
  }

  def deleteBucket(String bucketName) {
    ObjectListing objectListing = s3Client.listObjects(bucketName)
      Iterator<S3ObjectSummary> objIter = objectListing.getObjectSummaries().iterator()
      while (objIter.hasNext()) {
        s3Client.deleteObject(bucketName, objIter.next().getKey())
      }
    s3Client.deleteBucket(bucketName)
  }

  def enableS3Notifications(String bucketName, String sqsQueueArn) {
    BucketNotificationConfiguration notificationConfiguration = new BucketNotificationConfiguration()
    notificationConfiguration.addConfiguration("sqsQueueConfig",
      new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)))
    s3Client.setBucketNotificationConfiguration(new SetBucketNotificationConfigurationRequest(
      bucketName, notificationConfiguration))
  }

  @Ignore("Requires https://github.com/localstack/localstack/issues/3686 to work with localstack")
  def "simple S3 upload as producer - SQS consumer services"() {
    setup:
    String queueName = "s3ToSqsTestQueue"
    String bucketName = "s3-sqs-test-bucket"

    String queueUrl = createQueue(queueName)
    String queueArn = getQueueArn(queueUrl)
    setQueuePolicy(queueUrl, queueArn)
    createBucket(bucketName)
    enableS3Notifications(bucketName, queueArn)

    when:
    Thread.sleep(2000)
    // test message, auto created by AWS
    sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl))
    s3Client.putObject(bucketName, "testKey", "testData")
    Thread.sleep(3000)
    // traced message
    sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl))
    // cleanup
    deleteBucket(bucketName)
    sqsClient.purgeQueue(new PurgeQueueRequest(queueUrl))

    then:
    assertTraces(13) {
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
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(1, 1) {

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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(2, 1) {

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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(3, 1) {

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
            "net.peer.port" {it == null || Number}
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
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(7, 2) {
        span(0) {
          name "S3.PutObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "PutObject"
            "aws.service" "Amazon S3"
            "http.flavor" "1.1"
            "http.method" "PUT"
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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(8, 1) {
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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(9, 1) {
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
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(10, 1) {
        span(0) {
          name "S3.DeleteObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteObject"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(11, 1) {
        span(0) {
          name "S3.DeleteBucket"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "DeleteBucket"
            "aws.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" "IP.TCP"
            "net.peer.port" {it == null || Number}
          }
        }
      }
      trace(12, 1) {
        span(0) {
          name "SQS.PurgeQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.operation" "PurgeQueue"
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

  def policy(String queueArn) {
    return String.format(SQS_POLICY, queueArn)
  }

  private static final String SQS_POLICY = "{" +
    "  \"Statement\": [" +
    "    {" +
    "      \"Effect\": \"Allow\"," +
    "      \"Principal\": \"*\"," +
    "      \"Action\": \"sqs:SendMessage\"," +
    "      \"Resource\": \"%s\"" +
    "    }]" +
    "}"
}