/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.GCP_PUBSUB;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.gcloud.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("deprecation") // using deprecated semconv
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = SpringCloudGcpTestApplication.class)
class SpringCloudGcpTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-cloud-gcp-5.0";
  private static final String PROJECT_ID = "otel-test-project";
  private static final String TOPIC = "test-topic";
  private static final String SUBSCRIPTION = "test-subscription";

  private static PubSubEmulatorContainer emulator;
  private static TopicAdminClient topicAdminClient;
  private static SubscriptionAdminClient subscriptionAdminClient;

  @Autowired PubSubTemplate pubSubTemplate;

  @DynamicPropertySource
  static void gcpProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.cloud.gcp.pubsub.emulator-host", () -> SpringCloudGcpTestApplication.emulatorHost);
    registry.add("spring.cloud.gcp.project-id", () -> PROJECT_ID);
  }

  @BeforeAll
  static void setUp() throws Exception {
    PubSubEmulatorContainer container =
        new PubSubEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"));
    container.withStartupTimeout(Duration.ofMinutes(3));
    emulator = container;
    emulator.start();
    SpringCloudGcpTestApplication.emulatorHost = emulator.getEmulatorEndpoint();

    // create the topic and subscription before the Spring context starts so that the channel
    // adapter's streaming pull subscription is already available when it connects
    NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();
    topicAdminClient =
        TopicAdminClient.create(
            TopicAdminSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .setTransportChannelProvider(
                    TopicAdminSettings.defaultGrpcTransportProviderBuilder()
                        .setEndpoint(SpringCloudGcpTestApplication.emulatorHost)
                        .setChannelConfigurator(channelBuilder -> channelBuilder.usePlaintext())
                        .build())
                .build());
    subscriptionAdminClient =
        SubscriptionAdminClient.create(
            SubscriptionAdminSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .setTransportChannelProvider(
                    SubscriptionAdminSettings.defaultGrpcTransportProviderBuilder()
                        .setEndpoint(SpringCloudGcpTestApplication.emulatorHost)
                        .setChannelConfigurator(channelBuilder -> channelBuilder.usePlaintext())
                        .build())
                .build());
    topicAdminClient.createTopic(ProjectTopicName.of(PROJECT_ID, TOPIC));
    subscriptionAdminClient.createSubscription(
        ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION),
        ProjectTopicName.of(PROJECT_ID, TOPIC),
        PushConfig.getDefaultInstance(),
        10);
  }

  @AfterAll
  static void cleanUp() {
    if (topicAdminClient != null) {
      topicAdminClient.close();
    }
    if (subscriptionAdminClient != null) {
      subscriptionAdminClient.close();
    }
    if (emulator != null) {
      emulator.stop();
    }
    SpringCloudGcpTestApplication.emulatorHost = null;
  }

  @Test
  void pubsubConsumerSpan() throws Exception {
    // clear startup telemetry so the assertions below only see the publish/consume cycle
    testing.clearData();

    String messageContent = "hello";
    CompletableFuture<String> messageFuture = new CompletableFuture<>();
    SpringCloudGcpTestApplication.messageHandler =
        payload ->
            testing.runWithSpan(
                "callback", () -> assertThat(messageFuture.complete(payload)).isTrue());

    testing.runWithSpan("parent", () -> pubSubTemplate.publish(TOPIC, messageContent));

    String result = messageFuture.get(30, SECONDS);
    assertThat(result).isEqualTo(messageContent);

    AtomicReference<SpanData> processSpan = new AtomicReference<>();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(INTERNAL, CONSUMER),
        // grpc instrumentation is disabled, so publishing does not produce a client span
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()),
        trace -> {
          String processSpanName =
              emitStableMessagingSemconv() ? "process " + SUBSCRIPTION : SUBSCRIPTION + " process";
          trace.hasSpansSatisfyingExactly(
              span -> {
                span.hasName(processSpanName)
                    .hasKind(CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(
                        equalTo(MESSAGING_SYSTEM, GCP_PUBSUB),
                        oldOperation("process"),
                        operationName("process"),
                        operationType("process"),
                        equalTo(MESSAGING_DESTINATION_NAME, SUBSCRIPTION),
                        subscriptionName(SUBSCRIPTION),
                        satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                        bodySize());
                processSpan.set(trace.getSpan(0));
              },
              span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0)));
        });

    // the message carries no propagation attributes, so the process span has no links
    assertThat(processSpan.get().getLinks()).isEmpty();

    if (emitStableMessagingSemconv()) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "messaging.client.consumed.messages",
          metrics ->
              metrics.satisfiesExactly(
                  metric ->
                      assertThat(metric)
                          .hasUnit("{message}")
                          .hasDescription(
                              "Number of messages that were delivered to the application.")
                          .hasLongSumSatisfying(
                              sum ->
                                  sum.satisfies(data -> assertThat(data.getPoints()).hasSize(1))
                                      .hasPointsSatisfying(
                                          point ->
                                              point
                                                  .hasValue(1)
                                                  .hasAttributesSatisfyingExactly(
                                                      equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                      equalTo(MESSAGING_SYSTEM, GCP_PUBSUB),
                                                      equalTo(ERROR_TYPE, null),
                                                      equalTo(
                                                          MESSAGING_DESTINATION_NAME, SUBSCRIPTION),
                                                      equalTo(
                                                          MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                          SUBSCRIPTION))))));
    } else {
      // old messaging semconv does not emit any stable messaging metrics
      assertThat(testing.metrics())
          .filteredOn(
              metric ->
                  metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                      && metric.getName().startsWith("messaging."))
          .isEmpty();
    }
  }

  @Test
  void pubsubConsumerSpanWithPropagation() throws Exception {
    testing.clearData();

    String messageContent = "propagated";
    AtomicReference<SpanContext> producerContext = new AtomicReference<>();
    Map<String, String> attributes = new HashMap<>();
    CompletableFuture<String> messageFuture = new CompletableFuture<>();
    SpringCloudGcpTestApplication.messageHandler =
        payload ->
            testing.runWithSpan(
                "callback", () -> assertThat(messageFuture.complete(payload)).isTrue());

    testing.runWithSpan(
        "propagation-parent",
        () -> {
          SpanContext spanContext = Span.current().getSpanContext();
          producerContext.set(spanContext);
          attributes.put(
              "traceparent",
              "00-" + spanContext.getTraceId() + "-" + spanContext.getSpanId() + "-01");
          pubSubTemplate.publish(
              TOPIC,
              PubsubMessage.newBuilder()
                  .setData(ByteString.copyFromUtf8(messageContent))
                  .putAllAttributes(attributes)
                  .build());
        });

    String result = messageFuture.get(30, SECONDS);
    assertThat(result).isEqualTo(messageContent);

    String processSpanName =
        emitStableMessagingSemconv() ? "process " + SUBSCRIPTION : SUBSCRIPTION + " process";

    if (emitStableMessagingSemconv()) {
      // the propagated creation context is both the parent of the process span and its link
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("propagation-parent").hasNoParent(),
                  span ->
                      span.hasName(processSpanName)
                          .hasKind(CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasLinks(
                              LinkData.create(
                                  // the link points to the creation context extracted from the
                                  // traceparent attribute
                                  SpanContext.create(
                                      producerContext.get().getTraceId(),
                                      producerContext.get().getSpanId(),
                                      TraceFlags.getSampled(),
                                      TraceState.getDefault())))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, GCP_PUBSUB),
                              oldOperation("process"),
                              operationName("process"),
                              operationType("process"),
                              equalTo(MESSAGING_DESTINATION_NAME, SUBSCRIPTION),
                              subscriptionName(SUBSCRIPTION),
                              satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                              bodySize()),
                  span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(1))));
    } else {
      // the propagated context becomes the parent of the process span
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("propagation-parent").hasNoParent(),
                  span ->
                      span.hasName(processSpanName)
                          .hasKind(CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(MESSAGING_SYSTEM, GCP_PUBSUB),
                              oldOperation("process"),
                              operationName("process"),
                              operationType("process"),
                              equalTo(MESSAGING_DESTINATION_NAME, SUBSCRIPTION),
                              subscriptionName(SUBSCRIPTION),
                              satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                              bodySize()),
                  span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(1))));
    }
  }

  private static AttributeAssertion oldOperation(String operation) {
    return equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationName(String operation) {
    return equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion operationType(String operation) {
    return equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operation : null);
  }

  private static AttributeAssertion subscriptionName(String subscriptionName) {
    return equalTo(
        MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
        emitStableMessagingSemconv() ? subscriptionName : null);
  }

  // messaging.message.body.size is opt-in in the v1.43 messaging semantic conventions
  private static AttributeAssertion bodySize() {
    return emitOldMessagingSemconv()
        ? satisfies(MESSAGING_MESSAGE_BODY_SIZE, AbstractLongAssert::isNotNegative)
        : equalTo(MESSAGING_MESSAGE_BODY_SIZE, null);
  }
}
