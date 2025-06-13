/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SNS_TOPIC_ARN;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.PublishRequest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractSnsClientTest extends AbstractBaseAwsClientTest {
  private static final String publishResponseBody =
      "<PublishResponse xmlns=\"https://sns.amazonaws.com/doc/2010-03-31/\">"
          + "    <PublishResult>"
          + "        <MessageId>567910cd-659e-55d4-8ccb-5aaf14679dc0</MessageId>"
          + "    </PublishResult>"
          + "    <ResponseMetadata>"
          + "        <RequestId>d74b8436-ae13-5ab4-a9ff-ce54dfea72a0</RequestId>"
          + "    </ResponseMetadata>"
          + "</PublishResponse>";

  private static final String createTopicResponseBody =
      "<CreateTopicResponse xmlns=\"https://sns.amazonaws.com/doc/2010-03-31/\">"
          + "    <CreateTopicResult>"
          + "        <TopicArn>arn:aws:sns:us-east-1:123456789012:sns-topic-foo</TopicArn>"
          + "    </CreateTopicResult>"
          + "    <ResponseMetadata>"
          + "        <RequestId>d74b8436-ae13-5ab4-a9ff-ce54dfea72a0</RequestId>"
          + "    </ResponseMetadata>"
          + "</CreateTopicResponse>";

  public abstract AmazonSNSClientBuilder configureClient(AmazonSNSClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return true;
  }

  @Test
  public void testPublishRequestWithTargetArnAndMockedResponse() throws Exception {
    AmazonSNSClientBuilder clientBuilder = AmazonSNSClientBuilder.standard();
    AmazonSNS client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, publishResponseBody));
    List<AttributeAssertion> additionalAttributes =
        singletonList(equalTo(MESSAGING_DESTINATION_NAME, "target-arn-foo"));

    Object response =
        client.publish(
            new PublishRequest().withMessage("somemessage").withTargetArn("target-arn-foo"));
    assertRequestWithMockedResponse(
        response, client, "SNS", "Publish", "POST", additionalAttributes);
  }

  @Test
  public void testPublishRequestWithTopicArnAndMockedResponse() throws Exception {
    AmazonSNSClientBuilder clientBuilder = AmazonSNSClientBuilder.standard();
    AmazonSNS client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, publishResponseBody));
    List<AttributeAssertion> additionalAttributes =
        asList(
            equalTo(MESSAGING_DESTINATION_NAME, "topic-arn-foo"),
            equalTo(AWS_SNS_TOPIC_ARN, "topic-arn-foo"));

    Object response =
        client.publish(
            new PublishRequest().withMessage("somemessage").withTopicArn("topic-arn-foo"));

    assertRequestWithMockedResponse(
        response, client, "SNS", "Publish", "POST", additionalAttributes);
  }

  @Test
  public void testCreateTopicRequestWithMockedResponse() throws Exception {
    AmazonSNSClientBuilder clientBuilder = AmazonSNSClientBuilder.standard();
    AmazonSNS client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(
        HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, createTopicResponseBody));
    List<AttributeAssertion> additionalAttributes =
        asList(equalTo(AWS_SNS_TOPIC_ARN, "arn:aws:sns:us-east-1:123456789012:sns-topic-foo"));

    Object response = client.createTopic(new CreateTopicRequest().withName("sns-topic-foo"));

    assertRequestWithMockedResponse(
        response, client, "SNS", "CreateTopic", "POST", additionalAttributes);
  }
}
