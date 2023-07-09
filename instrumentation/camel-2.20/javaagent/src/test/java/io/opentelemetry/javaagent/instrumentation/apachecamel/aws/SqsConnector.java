/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqsConnector {

  private static final Logger logger = LoggerFactory.getLogger(SqsConnector.class);

  private SQSRestServer sqsRestServer;

  private AmazonSQS sqsClient;

  static SqsConnector elasticMq() {
    SqsConnector awsConnector = new SqsConnector();
    int sqsPort = PortUtils.findOpenPort();
    awsConnector.sqsRestServer =
        SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq");
    awsConnector.sqsClient =
        AmazonSQSAsyncClient.asyncBuilder()
            .withCredentials(credentials)
            .withEndpointConfiguration(endpointConfiguration)
            .build();

    return awsConnector;
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

  AmazonSQS getSqsClient() {
    return sqsClient;
  }
}
