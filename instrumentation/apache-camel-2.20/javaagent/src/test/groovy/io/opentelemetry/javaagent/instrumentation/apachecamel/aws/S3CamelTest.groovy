/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap
import spock.lang.Ignore
import spock.lang.Shared

import static io.opentelemetry.api.trace.SpanKind.CONSUMER

@Ignore("Does not work with localstack - X-Ray features needed")
class S3CamelTest extends AgentInstrumentationSpecification {

  @Shared
  AwsConnector awsConnector = AwsConnector.liveAws()

  def "camel S3 producer - camel SQS consumer"() {
    setup:
    String bucketName = "bucket-test-s3-sqs-camel"
    String queueName = "s3SqsCamelTest"
    def camelApp = new CamelSpringApp(awsConnector, S3Config, ImmutableMap.of("bucketName", bucketName, "queueName", queueName))

    def queueUrl = setupTestInfrastructure(queueName, bucketName)
    waitAndClearSetupTraces(queueUrl, queueName, bucketName)

    when:
    camelApp.start()
    camelApp.producerTemplate().sendBody("direct:input", "{\"type\": \"hello\"}")

    then:
    assertTraces(6) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.ListQueues")
      }
      trace(1, 1) {
        AwsSpan.s3(it, 0, "S3.ListObjects", bucketName)
      }
      trace(2, 5) {
        CamelSpan.direct(it, 0, "input")
        CamelSpan.s3(it, 1, span(0))
        AwsSpan.s3(it, 2, "S3.PutObject", bucketName, "PUT", span(1))
        AwsSpan.sqs(it, 3, "SQS.ReceiveMessage", queueUrl, null, CONSUMER, span(2))
        CamelSpan.sqsConsume(it, 4, queueName, span(2))
      }
      // HTTP "client" receiver span, one per each SQS request
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      // camel polling
      trace(4, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)

      }
      // camel cleaning received msg
      trace(5, 1) {
        AwsSpan.sqs(it, 0, "SQS.DeleteMessage", queueUrl)
      }
    }

    cleanup:
    awsConnector.deleteBucket(bucketName)
    awsConnector.purgeQueue(queueUrl)
    camelApp.stop()
  }

  def setupTestInfrastructure(queueName, bucketName) {
    // setup infra
    String queueUrl = awsConnector.createQueue(queueName)
    awsConnector.createBucket(bucketName)
    String queueArn = awsConnector.getQueueArn(queueUrl)
    awsConnector.setQueuePublishingPolicy(queueUrl, queueArn)
    awsConnector.enableS3ToSqsNotifications(bucketName, queueArn)

    // consume test message from AWS
    awsConnector.receiveMessage(queueUrl)

    return queueUrl
  }

  def waitAndClearSetupTraces(queueUrl, queueName, bucketName) {
    assertTraces(7) {
      trace(0, 1) {
        AwsSpan.sqs(it, 0, "SQS.CreateQueue", queueUrl, queueName)
      }
      trace(1, 1) {
        AwsSpan.s3(it, 0, "S3.CreateBucket", bucketName, "PUT")
      }
      trace(2, 1) {
        AwsSpan.sqs(it, 0, "SQS.GetQueueAttributes", queueUrl)
      }
      trace(3, 1) {
        AwsSpan.sqs(it, 0, "SQS.SetQueueAttributes", queueUrl)
      }
      trace(4, 1) {
        AwsSpan.s3(it, 0, "S3.SetBucketNotificationConfiguration", bucketName, "PUT")
      }
      trace(5, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl)
      }
      trace(6, 1) {
        AwsSpan.sqs(it, 0, "SQS.ReceiveMessage", queueUrl, null, CONSUMER)
      }
    }
    clearExportedData()
  }
}