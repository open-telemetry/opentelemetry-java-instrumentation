/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.util.Collections.singletonMap;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.QueueConfiguration;
import com.amazonaws.services.s3.model.S3Event;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest;
import com.amazonaws.services.s3.model.TopicConfiguration;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

class AwsConnector {
  private final LocalStackContainer localStack;
  private final AmazonSQSAsync sqsClient;
  private final AmazonS3 s3Client;
  private final AmazonSNSAsync snsClient;

  AwsConnector() {
    localStack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices("sqs", "sns", "s3")
            .withEnv("DEBUG", "1")
            .withEnv("SQS_PROVIDER", "elasticmq")
            .withEnv("SQS_ENDPOINT_STRATEGY", "path")
            .withStartupTimeout(Duration.ofMinutes(2));
    localStack.start();
    localStack.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("test")));

    AWSCredentialsProvider credentialsProvider =
        new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(localStack.getAccessKey(), localStack.getSecretKey()));

    sqsClient =
        AmazonSQSAsyncClient.asyncBuilder()
            .withEndpointConfiguration(getEndpointConfiguration(localStack))
            .withCredentials(credentialsProvider)
            .build();

    s3Client =
        AmazonS3Client.builder()
            .withEndpointConfiguration(getEndpointConfiguration(localStack))
            .withCredentials(credentialsProvider)
            .build();

    snsClient =
        AmazonSNSAsyncClient.asyncBuilder()
            .withEndpointConfiguration(getEndpointConfiguration(localStack))
            .withCredentials(credentialsProvider)
            .build();
  }

  static AwsClientBuilder.EndpointConfiguration getEndpointConfiguration(
      LocalStackContainer localStack) {
    return new AwsClientBuilder.EndpointConfiguration(
        localStack.getEndpoint().toString(), localStack.getRegion());
  }

  String createQueue(String queueName) {
    return externalQueueUrl(sqsClient.createQueue(queueName).getQueueUrl());
  }

  String getQueueArn(String queueUrl) {
    return sqsClient
        .getQueueAttributes(
            new GetQueueAttributesRequest(externalQueueUrl(queueUrl))
                .withAttributeNames("QueueArn"))
        .getAttributes()
        .get("QueueArn");
  }

  void setTopicPublishingPolicy(String topicArn) {
    snsClient.setTopicAttributes(
        new SetTopicAttributesRequest(
            topicArn,
            "Policy",
            String.format(
                "{"
                    + "  \"Statement\": ["
                    + "    {"
                    + "      \"Effect\": \"Allow\","
                    + "      \"Principal\": \"*\","
                    + "      \"Action\": \"sns:Publish\","
                    + "      \"Resource\": \"%s\""
                    + "    }]"
                    + "}",
                topicArn)));
  }

  void setQueuePublishingPolicy(String queueUrl, String queueArn) {
    sqsClient.setQueueAttributes(
        externalQueueUrl(queueUrl),
        singletonMap(
            "Policy",
            String.format(
                "{"
                    + "  \"Statement\": ["
                    + "    {"
                    + "      \"Effect\": \"Allow\","
                    + "      \"Principal\": \"*\","
                    + "      \"Action\": \"sqs:SendMessage\","
                    + "      \"Resource\": \"%s\""
                    + "    }]"
                    + "}",
                queueArn)));
  }

  void createBucket(String bucketName) {
    s3Client.createBucket(bucketName);
  }

  void deleteBucket(String bucketName) {
    ObjectListing objectListing = s3Client.listObjects(bucketName);
    for (S3ObjectSummary element : objectListing.getObjectSummaries()) {
      s3Client.deleteObject(bucketName, element.getKey());
    }
    s3Client.deleteBucket(bucketName);
  }

  void enableS3ToSqsNotifications(String bucketName, String sqsQueueArn) {
    BucketNotificationConfiguration notificationConfiguration =
        new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration(
        "sqsQueueConfig",
        new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(
        new SetBucketNotificationConfigurationRequest(bucketName, notificationConfiguration));
  }

  void enableS3ToSnsNotifications(String bucketName, String snsTopicArn) {
    BucketNotificationConfiguration notificationConfiguration =
        new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration(
        "snsTopicConfig",
        new TopicConfiguration(snsTopicArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(
        new SetBucketNotificationConfigurationRequest(bucketName, notificationConfiguration));
  }

  String createTopicAndSubscribeQueue(String topicName, String queueArn) {
    CreateTopicResult ctr = snsClient.createTopic(topicName);
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn);
    return ctr.getTopicArn();
  }

  ReceiveMessageResult receiveMessage(String queueUrl) {
    return sqsClient.receiveMessage(
        new ReceiveMessageRequest(externalQueueUrl(queueUrl)).withWaitTimeSeconds(20));
  }

  void purgeQueue(String queueUrl) {
    sqsClient.purgeQueue(new PurgeQueueRequest(externalQueueUrl(queueUrl)));
  }

  void putSampleData(String bucketName) {
    s3Client.putObject(bucketName, "otelTestKey", "otelTestData");
  }

  void publishSampleNotification(String topicArn) {
    snsClient.publish(topicArn, "Hello There");
  }

  void disconnect() {
    if (localStack != null) {
      localStack.stop();
    }
  }

  private String externalQueueUrl(String queueUrl) {
    URI endpoint = localStack.getEndpoint();
    URI queueUri = URI.create(queueUrl);
    return endpoint.resolve(queueUri.getRawPath()).toString();
  }
}
