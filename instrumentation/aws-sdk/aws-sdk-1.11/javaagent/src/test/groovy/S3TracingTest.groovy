/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.NetworkAttributes
import io.opentelemetry.semconv.UrlAttributes
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.CONSUMER

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
    def receiveMessageResult = awsConnector.receiveMessage(queueUrl)
    receiveMessageResult.messages.each {message ->
      runWithSpan("process child") {}
    }

    // cleanup
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)

    then:
    assertTraces(10) {
      trace(0, 1) {

        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.name" queueName
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "CreateQueue"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "aws.bucket.name" bucketName
            "rpc.method" "CreateBucket"
            "rpc.system" "aws-api"
            "rpc.service" "Amazon S3"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "GetQueueAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "SetQueueAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(5, 3) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
        span(1) {
          name "s3ToSqsTestQueue process"
          kind CONSUMER
          childOf span(0)
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "ReceiveMessage"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" MessagingIncubatingAttributes.MessagingSystemValues.AWS_SQS
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "s3ToSqsTestQueue"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
          }
        }
        span(2) {
          name "process child"
          childOf span(1)
          attributes {
          }
        }
      }
      trace(6, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "GET"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(7, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "DELETE"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 204
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(8, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "DELETE"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 204
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(9, 1) {
        span(0) {
          name "SQS.PurgeQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "PurgeQueue"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
    def receiveMessageResult = awsConnector.receiveMessage(queueUrl)
    receiveMessageResult.messages.each {message ->
      runWithSpan("process child") {}
    }
    // cleanup
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)

    then:
    assertTraces(14) {
      trace(0, 1) {
        span(0) {
          name "SQS.CreateQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.name" queueName
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "CreateQueue"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "GetQueueAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "CreateTopic"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "Subscribe"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" topicArn
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
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "SetQueueAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
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
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "SetTopicAttributes"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSNS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" topicArn
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(8, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "PUT"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(9, 2) {
        span(0) {
          name "s3ToSnsToSqsTestQueue process"
          kind CONSUMER
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "ReceiveMessage"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
            "$MessagingIncubatingAttributes.MESSAGING_SYSTEM" MessagingIncubatingAttributes.MessagingSystemValues.AWS_SQS
            "$MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME" "s3ToSnsToSqsTestQueue"
            "$MessagingIncubatingAttributes.MESSAGING_OPERATION" "process"
            "$MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
          }
        }
        span(1) {
          name "process child"
          childOf span(0)
          attributes {
          }
        }
      }
      trace(10, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "GET"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(11, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "DELETE"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 204
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(12, 1) {
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
            "$HttpAttributes.HTTP_REQUEST_METHOD" "DELETE"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 204
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
      trace(13, 1) {
        span(0) {
          name "SQS.PurgeQueue"
          kind CLIENT
          hasNoParent()
          attributes {
            "aws.agent" "java-aws-sdk"
            "aws.endpoint" String
            "aws.queue.url" queueUrl
            "$AwsIncubatingAttributes.AWS_REQUEST_ID" String
            "rpc.method" "PurgeQueue"
            "rpc.system" "aws-api"
            "rpc.service" "AmazonSQS"
            "$HttpAttributes.HTTP_REQUEST_METHOD" "POST"
            "$HttpAttributes.HTTP_RESPONSE_STATUS_CODE" 200
            "$UrlAttributes.URL_FULL" { it.startsWith("http://") }
            "$ServerAttributes.SERVER_ADDRESS" String
            "$NetworkAttributes.NETWORK_PROTOCOL_VERSION" "1.1"
            "$ServerAttributes.SERVER_PORT" { it == null || Number }
          }
        }
      }
    }
  }
}
