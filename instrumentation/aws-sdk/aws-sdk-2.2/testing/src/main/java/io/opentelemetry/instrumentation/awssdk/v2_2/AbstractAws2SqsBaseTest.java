package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractAws2SqsBaseTest {

  protected abstract InstrumentationExtension getTesting();

  protected abstract SqsClient configureSqsClient(SqsClient sqsClient);

  protected abstract SqsAsyncClient configureSqsClient(SqsAsyncClient sqsClient);

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  protected static int sqsPort;
  protected static SQSRestServer sqs;

  static Map<String, MessageAttributeValue> dummyMessageAttributes(int count) {
    Map<String, MessageAttributeValue> map = new HashMap<>();
    for (int i = 0; i < count; i++) {
      map.put(
          "a" + i, MessageAttributeValue.builder().stringValue("v" + i).dataType("String").build());
    }
    return map;
  }

  private final String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";

  ReceiveMessageRequest receiveMessageRequest =
      ReceiveMessageRequest.builder().queueUrl(queueUrl).build();

  ReceiveMessageRequest receiveMessageBatchRequest =
      ReceiveMessageRequest.builder()
          .queueUrl(queueUrl)
          .maxNumberOfMessages(3)
          .messageAttributeNames("All")
          .waitTimeSeconds(5)
          .build();

  CreateQueueRequest createQueueRequest =
      CreateQueueRequest.builder().queueName("testSdkSqs").build();

  SendMessageRequest sendMessageRequest =
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody("{\"type\": \"hello\"}").build();

  @SuppressWarnings("unchecked")
  SendMessageBatchRequest sendMessageBatchRequest =
      SendMessageBatchRequest.builder()
          .queueUrl(queueUrl)
          .entries(
              e -> e.messageBody("e1").id("i1"),
              // 8 attributes, injection always possible
              e -> e.messageBody("e2").id("i2").messageAttributes(dummyMessageAttributes(8)),
              // 10 attributes, injection with custom propagator never possible
              e -> e.messageBody("e3").id("i3").messageAttributes(dummyMessageAttributes(10)))
          .build();

  boolean isXrayInjectionEnabled() {
    return true;
  }

  protected void configureSdkClient(SqsClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  void configureSdkClient(SqsAsyncClientBuilder builder) throws URISyntaxException {
    builder
        .overrideConfiguration(createOverrideConfigurationBuilder().build())
        .endpointOverride(new URI("http://localhost:" + sqsPort));
    builder.region(Region.AP_NORTHEAST_1).credentialsProvider(CREDENTIALS_PROVIDER);
  }

  protected abstract void assertSqsTraces(Boolean withParent, Boolean captureHeaders);

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

}
