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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testSendRequestWithMockedResponse(
      Function<AmazonSNS, Object> call,
      String operation,
      String responseBody,
      List<AttributeAssertion> additionalAttributes)
      throws Exception {
    AmazonSNSClientBuilder clientBuilder = AmazonSNSClientBuilder.standard();
    AmazonSNS client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, responseBody));
    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "SNS", operation, "POST", additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            (Function<AmazonSNS, Object>)
                c ->
                    c.publish(
                        new PublishRequest().withMessage("somemessage").withTopicArn("somearn")),
            "Publish",
            publishResponseBody,
            asList(
                equalTo(MESSAGING_DESTINATION_NAME, "somearn"),
                equalTo(AWS_SNS_TOPIC_ARN, "somearn"))),
        Arguments.of(
            (Function<AmazonSNS, Object>)
                c ->
                    c.publish(
                        new PublishRequest().withMessage("somemessage").withTargetArn("somearn")),
            "Publish",
            publishResponseBody,
            singletonList(equalTo(MESSAGING_DESTINATION_NAME, "somearn"))),
        Arguments.of(
            (Function<AmazonSNS, Object>)
                c -> c.createTopic(new CreateTopicRequest().withName("sns-topic-foo")),
            "CreateTopic",
            createTopicResponseBody,
            singletonList(
                equalTo(AWS_SNS_TOPIC_ARN, "arn:aws:sns:us-east-1:123456789012:sns-topic-foo"))));
  }
}
