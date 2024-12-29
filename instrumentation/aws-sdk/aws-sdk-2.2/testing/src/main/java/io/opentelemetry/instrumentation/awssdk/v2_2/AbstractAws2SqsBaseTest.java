/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.http.scaladsl.Http;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public abstract class AbstractAws2SqsBaseTest {
  protected static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));
  protected static int sqsPort;
  protected static SQSRestServer sqs;
  protected final String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";

  protected ReceiveMessageRequest receiveMessageRequest =
      ReceiveMessageRequest.builder().queueUrl(queueUrl).build();

  protected ReceiveMessageRequest receiveMessageBatchRequest =
      ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(3)
          .messageAttributeNames("All")
          .waitTimeSeconds(5)
          .build();

  protected CreateQueueRequest createQueueRequest =
      CreateQueueRequest.builder().queueName("testSdkSqs").build();

  protected SendMessageRequest sendMessageRequest =
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{\"type\": \"hello\"}").build();

  @SuppressWarnings("unchecked")
  protected SendMessageBatchRequest sendMessageBatchRequest =
      SendMessageBatchRequest.builder()
          .queueUrl(queueUrl)
          .entries(
              e -> e.messageBody("e1").id("i1"),
              // 8 attributes, injection always possible
              e -> e.messageBody("e2").id("i2").messageAttributes(dummyMessageAttributes(8)),
              // 10 attributes, injection with custom propagator never possible
              e -> e.messageBody("e3").id("i3").messageAttributes(dummyMessageAttributes(10)))
          .build();

  protected abstract InstrumentationExtension getTesting();

  protected abstract SqsClient configureSqsClient(SqsClient sqsClient);

  protected abstract SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient);

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected abstract void assertSqsTraces(boolean withParent, boolean captureHeaders);

  static Map<String, MessageAttributeValue> dummyMessageAttributes(int count) {
    Map<String, MessageAttributeValue> map = new HashMap<>();
    for (int i = 0; i < count; i++) {
      map.put(
          "a" + i, MessageAttributeValue.builder().stringValue("v" + i).dataType("String").build());
    }
    return map;
  }

  protected boolean isXrayInjectionEnabled() {
    return true;
  }

  protected void configureSdkClient(SqsClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  protected void configureSdkClient(SqsAsyncClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  protected boolean isSqsAttributeInjectionEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  @BeforeAll
  static void setUp() {
    sqs = SQSRestServerBuilder.withPort(0).withInterface("localhost").start();
    Http.ServerBinding server = sqs.waitUntilStarted();
    sqsPort = server.localAddress().getPort();
  }

  @AfterAll
  static void cleanUp() {
    if (sqs != null) {
      sqs.stopAndWait();
    }
  }

  @Test
  void testSimpleSqsProducerConsumerServicesSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  @Test
  void testSimpleSqsProducerConsumerServicesWithParentSync() throws URISyntaxException {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);

    ReceiveMessageResponse response =
        getTesting().runWithSpan("parent", () -> client.receiveMessage(receiveMessageRequest));

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(true, false);
  }

  @SuppressWarnings("InterruptedExceptionSwallowed")
  @Test
  void testSimpleSqsProducerConsumerServicesAsync() throws Exception {
    SqsAsyncClientBuilder builder = SqsAsyncClient.builder();
    configureSdkClient(builder);
    SqsAsyncClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest).get();
    client.sendMessage(sendMessageRequest).get();

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest).get();

    assertThat(response.messages().size()).isEqualTo(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, false);
  }

  static SpanDataAssert createQueueSpan(SpanDataAssert span) {
    return span.hasName("Sqs.CreateQueue")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
            satisfies(
                AWS_REQUEST_ID,
                val -> val.matches("\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Sqs"),
            equalTo(RPC_METHOD, "CreateQueue"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, sqsPort));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  static SpanDataAssert processSpan(SpanDataAssert span, SpanData parent) {
    return span.hasName("testSdkSqs process")
        .hasKind(SpanKind.CONSUMER)
        .hasParent(parent)
        .hasTotalRecordedLinks(0)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Sqs"),
            equalTo(RPC_METHOD, "ReceiveMessage"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, sqsPort),
            equalTo(MESSAGING_SYSTEM, AWS_SQS),
            equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
            equalTo(MESSAGING_OPERATION, "process"),
            satisfies(MESSAGING_MESSAGE_ID, v -> v.isInstanceOf(String.class)));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  static SpanDataAssert publishSpan(SpanDataAssert span, String queueUrl, String rcpMethod) {
    return span.hasName("testSdkSqs publish")
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(stringKey("aws.queue.url"), queueUrl),
            satisfies(
                AWS_REQUEST_ID,
                val -> val.matches("\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Sqs"),
            equalTo(RPC_METHOD, rcpMethod),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            satisfies(URL_FULL, v -> v.startsWith("http://localhost:" + sqsPort)),
            equalTo(SERVER_ADDRESS, "localhost"),
            equalTo(SERVER_PORT, sqsPort),
            equalTo(MESSAGING_SYSTEM, AWS_SQS),
            equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
            equalTo(MESSAGING_OPERATION, "publish"),
            satisfies(
                MESSAGING_MESSAGE_ID,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isInstanceOf(String.class),
                        v -> assertThat(v).isNull())));
  }
}
