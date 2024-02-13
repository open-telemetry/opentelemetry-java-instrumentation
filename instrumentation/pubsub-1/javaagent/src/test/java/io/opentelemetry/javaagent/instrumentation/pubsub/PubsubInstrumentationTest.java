package io.opentelemetry.javaagent.instrumentation.pubsub;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.SemanticAttributes.MESSAGING_MESSAGE_ID;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.cloud.pubsub.v1.stub.GrpcPublisherStub;
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

public class PubsubInstrumentationTest {
  private static PubSubEmulatorContainer emulator;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void beforeAll() {
    emulator = new PubSubEmulatorContainer(
        DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:463.0.0-emulators")
    );
    emulator.start();
  }

  @AfterAll
  static void afterAll() {
    if (emulator != null) {
      emulator.close();
    }
  }

  @Test
  public void publishAndSubscribe() throws Exception {
    FixedTransportChannelProvider channelProvider = FixedTransportChannelProvider.create(
            GrpcTransportChannel.create(
                    ManagedChannelBuilder.forTarget(emulator.getEmulatorEndpoint()).usePlaintext().build()));
    NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();

    // Create topic
    int randomNumber = new Random().nextInt();
    String topicName = String.format("test.topic-%d", randomNumber);
    String topic = String.format("projects/kognic-doesnt-exist/topics/%s", topicName);
    try (TopicAdminClient topicClient = TopicAdminClient.create(
            TopicAdminSettings.newBuilder()
                    .setTransportChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build())) {
      topicClient.createTopic(topic);
    }

    // Create subscription
    String subscriptionName = String.format("test.sub-%d", randomNumber);
    String subscription = String.format("projects/kognic-doesnt-exist/subscriptions/%s", subscriptionName);
    try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(
            SubscriptionAdminSettings.newBuilder()
                    .setTransportChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build())) {
      subscriptionAdminClient.createSubscription(subscription, topic, PushConfig.getDefaultInstance(), 20);
    }

    // Publish a message
    PublishRequest request = PublishRequest.newBuilder()
            .addMessages(PubsubMessage.newBuilder().setData(ByteString.copyFrom("data", "UTF-8")))
            .setTopic(topic)
            .build();
    String msgId = null;
    try (GrpcPublisherStub grpcPublisher = GrpcPublisherStub.create(PublisherStubSettings.newBuilder()
            .setTransportChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider)
            .build())) {
      PublishResponse response = testing.runWithSpan("parent", () -> grpcPublisher.publishCallable().call(request));
      msgId = response.getMessageIds(0);
    }

    // Subscribe until the message is received
    AtomicReference<PubsubMessage> receivedMessage = new AtomicReference<>();
    MessageReceiver receiver = (msg, ack) -> {
      ack.ack();
      receivedMessage.set(msg);
    };
    Subscriber subscriber = Subscriber.newBuilder(subscription, receiver)
            .setChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider)
            .build();

    subscriber.startAsync().awaitRunning(10, TimeUnit.SECONDS);
    Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> receivedMessage.get() != null);
    subscriber.stopAsync().awaitTerminated(10, TimeUnit.SECONDS);

    List<SpanData> spans = testing.spans();

    SpanData parent = spans.stream().filter(s -> s.getName().equals("parent")).findFirst().get();
    SpanData publish = spans.stream().filter(s -> s.getName().equals(String.format("%s publish", topicName))).findFirst().get();
    SpanData create = spans.stream().filter(s -> s.getName().equals(String.format("%s create", topicName))).findFirst().get();
    SpanData receive = spans.stream().filter(s -> s.getName().equals(String.format("%s receive", subscriptionName))).findFirst().get();

    assertThat(publish).hasParent(parent).hasEnded();
    assertThat(create).hasParent(parent).hasEnded()
            .hasAttribute(MESSAGING_MESSAGE_ID, msgId);
    assertThat(receive).hasParent(create).hasEnded()
            .hasAttribute(AttributeKey.stringKey("messaging.gcp_pubsub.message.ack_result"), "ack")
            .hasAttribute(MESSAGING_MESSAGE_ID, msgId);
  }
}
