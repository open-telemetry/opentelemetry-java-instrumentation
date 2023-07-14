/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.BucketNotificationConfiguration;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.QueueConfiguration;
import com.amazonaws.services.s3.model.S3Event;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.SetBucketNotificationConfigurationRequest;
import com.amazonaws.services.sns.AmazonSNSAsyncClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import java.util.Collections;
import java.util.EnumSet;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AwsConnector {
  private static final Logger logger = LoggerFactory.getLogger(AwsConnector.class);
  private AmazonSQSAsyncClient sqsClient;
  private AmazonS3Client s3Client;
  private AmazonSNSAsyncClient snsClient;
  private SQSRestServer sqsRestServer;

  static AwsConnector elasticMq() {
    AwsConnector awsConnector = new AwsConnector();
    int sqsPort = PortUtils.findOpenPort();
    awsConnector.sqsRestServer =
        SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq");
    awsConnector.sqsClient =
        (AmazonSQSAsyncClient)
            AmazonSQSAsyncClient.asyncBuilder()
                .withCredentials(credentials)
                .withEndpointConfiguration(endpointConfiguration)
                .build();

    return awsConnector;
  }

  static AwsConnector liveAws() {
    AwsConnector awsConnector = new AwsConnector();

    awsConnector.sqsClient =
        (AmazonSQSAsyncClient)
            AmazonSQSAsyncClient.asyncBuilder()
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

    awsConnector.s3Client =
        (AmazonS3Client)
            AmazonS3Client.builder()
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

    awsConnector.snsClient = (AmazonSNSAsyncClient) AmazonSNSAsyncClient.asyncBuilder().build();

    return awsConnector;
  }

  void createBucket(String bucketName) {
    logger.info("Create bucket  " + bucketName);
    s3Client.createBucket(bucketName);
  }

  void deleteBucket(String bucketName) {
    logger.info("Delete bucket " + bucketName);
    ObjectListing objectListing = s3Client.listObjects(bucketName);
    for (S3ObjectSummary s3ObjectSummary : objectListing.getObjectSummaries()) {
      s3Client.deleteObject(bucketName, s3ObjectSummary.getKey());
    }
    s3Client.deleteBucket(bucketName);
  }

  void enableS3ToSqsNotifications(String bucketName, String sqsQueueArn) {
    logger.info("Enable notification for bucket " + bucketName + " to queue " + sqsQueueArn);
    BucketNotificationConfiguration notificationConfiguration =
        new BucketNotificationConfiguration();
    notificationConfiguration.addConfiguration(
        "sqsQueueConfig",
        new QueueConfiguration(sqsQueueArn, EnumSet.of(S3Event.ObjectCreatedByPut)));
    s3Client.setBucketNotificationConfiguration(
        new SetBucketNotificationConfigurationRequest(bucketName, notificationConfiguration));
  }

  String getQueueArn(String queueUrl) {
    logger.info("Get ARN for queue " + queueUrl);
    return sqsClient
        .getQueueAttributes(new GetQueueAttributesRequest(queueUrl).withAttributeNames("QueueArn"))
        .getAttributes()
        .get("QueueArn");
  }

  private static String getSqsPolicy(String resource) {
    return String.format(
        "{\"Statement\": [{\"Effect\": \"Allow\", \"Principal\": \"*\", \"Action\": \"sqs:SendMessage\", \"Resource\": \"%s\"}]}",
        resource);
  }

  void purgeQueue(String queueUrl) {
    logger.info("Purge queue " + queueUrl);
    sqsClient.purgeQueue(new PurgeQueueRequest(queueUrl));
  }

  void setQueuePublishingPolicy(String queueUrl, String queueArn) {
    logger.info("Set policy for queue " + queueArn);
    sqsClient.setQueueAttributes(
        queueUrl, Collections.singletonMap("Policy", getSqsPolicy(queueArn)));
  }

  String createQueue(String queueName) {
    logger.info("Create queue " + queueName);
    return sqsClient.createQueue(queueName).getQueueUrl();
  }

  void sendSampleMessage(String queueUrl) {
    SendMessageRequest send = new SendMessageRequest(queueUrl, "{\"type\": \"hello\"}");
    sqsClient.sendMessage(send);
  }

  void receiveMessage(String queueUrl) {
    logger.info("Receive message from queue " + queueUrl);
    sqsClient.receiveMessage(new ReceiveMessageRequest(queueUrl).withWaitTimeSeconds(20));
  }

  void disconnect() {
    if (sqsRestServer != null) {
      sqsRestServer.stopAndWait();
    }
  }

  void publishSampleNotification(String topicArn) {
    snsClient.publish(topicArn, "Hello There");
  }

  String createTopicAndSubscribeQueue(String topicName, String queueArn) {
    logger.info("Create topic " + topicName + " and subscribe to queue " + queueArn);
    CreateTopicResult ctr = snsClient.createTopic(topicName);
    snsClient.subscribe(ctr.getTopicArn(), "sqs", queueArn);
    return ctr.getTopicArn();
  }

  AmazonSQSAsyncClient getSqsClient() {
    return sqsClient;
  }

  AmazonS3Client getS3Client() {
    return s3Client;
  }

  AmazonSNSAsyncClient getSnsClient() {
    return snsClient;
  }
}
