/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class S3TracingTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.localstack()

  def cleanupSpec() {
    awsConnector.disconnect()
  }

  def "S3 upload triggers SQS message"() {
    setup:
    String queueName = "s3ToSqsTestQueue"
    String bucketName = "otel-s3-to-sqs-test-bucket"

    String queueUrl = awsConnector.createQueue(queueName)
    awsConnector.createBucket(bucketName)

    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn)

    when:
    // test message, auto created by AWS
    awsConnector.receiveMessage(queueUrl)
    awsConnector.putSampleData(bucketName)
    // traced message
    awsConnector.receiveMessage(queueUrl)
    // cleanup
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)

    then:
    assertTraces(12) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "CreateQueue"
            "aws.queue.name" queueName
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
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
            "rpc.method" "CreateBucket"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
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
            "rpc.method" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
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
            "rpc.method" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
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
            "rpc.method" "SetBucketNotificationConfiguration"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
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
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(6, 2) {
        span(0) {
          name "S3.PutObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "PutObject"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
        span(1) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }

      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(7, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(8, 1) {
        span(0) {
          name "S3.ListObjects"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ListObjects"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "GET"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(9, 1) {
        span(0) {
          name "S3.DeleteObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "DeleteObject"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(10, 1) {
        span(0) {
          name "S3.DeleteBucket"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "DeleteBucket"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(11, 1) {
        span(0) {
          name "SQS.PurgeQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "PurgeQueue"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
    }
  }

  def "S3 upload triggers SNS topic notification, then creates SQS message"() {
    setup:
    String queueName = "s3ToSnsToSqsTestQueue"
    String bucketName = "otel-s3-sns-sqs-test-bucket"
    String topicName = "s3ToSnsToSqsTestTopic"

    String queueUrl = awsConnector.createQueue(queueName)
    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.createBucket(bucketName)
    String topicArn = awsConnector.createTopicAndSubscribeQueue(topicName, queueArn)

    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    awsConnector.setTopicPublishingPolicy(topicArn)
    awsConnector.enableS3ToSnsNotifications(bucketName, topicArn)

    when:
    // test message, auto created by AWS
    awsConnector.receiveMessage(queueUrl)
    awsConnector.putSampleData(bucketName)
    // traced message
    awsConnector.receiveMessage(queueUrl)
    // cleanup
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)

    then:
    assertTraces(16) {
      trace(0, 1) {
        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "CreateQueue"
            "aws.queue.name" queueName
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
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
            "rpc.method" "GetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(2, 1) {
        span(0) {
          name "S3.CreateBucket"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "CreateBucket"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
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
            "rpc.method" "CreateTopic"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
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
            "rpc.method" "Subscribe"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(5, 1) {
        span(0) {
          name "SQS.SetQueueAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "SetQueueAttributes"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(6, 1) {
        span(0) {
          name "SNS.SetTopicAttributes"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "SetTopicAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(7, 1) {
        span(0) {
          name "S3.SetBucketNotificationConfiguration"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "SetBucketNotificationConfiguration"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      // test even receive
      trace(8, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(9, 1) {
        span(0) {
          name "S3.PutObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "PutObject"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "PUT"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      /**
       * This span represents HTTP "sending of receive message" operation. It's always single, while there can be multiple CONSUMER spans (one per consumed message).
       * This one could be suppressed (by IF in TracingRequestHandler#beforeRequest but then HTTP instrumentation span would appear
       */
      trace(10, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(11, 1) {
        span(0) {
          name "SQS.ReceiveMessage"
          kind CONSUMER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ReceiveMessage"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "http.user_agent" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(12, 1) {
        span(0) {
          name "S3.ListObjects"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "ListObjects"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "GET"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(13, 1) {
        span(0) {
          name "S3.DeleteObject"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "DeleteObject"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(14, 1) {
        span(0) {
          name "S3.DeleteBucket"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "DeleteBucket"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "aws.bucket.name" bucketName
            "http.flavor" "1.1"
            "http.method" "DELETE"
            "http.status_code" 204
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
      trace(15, 1) {
        span(0) {
          name "SQS.PurgeQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "rpc.method" "PurgeQueue"
            "aws.queue.url" queueUrl
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "http.flavor" "1.1"
            "http.method" "POST"
            "http.status_code" 200
            "http.url" String
            "net.peer.name" String
            "net.transport" IP_TCP
            "net.peer.port" { it == null || Number }
          }
        }
      }
    }
  }
}