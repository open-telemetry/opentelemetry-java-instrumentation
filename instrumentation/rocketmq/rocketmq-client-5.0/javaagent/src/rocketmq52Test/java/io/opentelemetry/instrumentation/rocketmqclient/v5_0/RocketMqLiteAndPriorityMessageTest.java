/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.attribute.AttributeParser;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RocketMqLiteAndPriorityMessageTest {
  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      Boolean.getBoolean(
          "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled");
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rocketmq-client-5.0";

  private static final String LITE_TOPIC = "lite-topic";
  private static final String LITE_CHILD_TOPIC = "lite-child";
  private static final String LITE_GROUP = "lite-group";
  private static final String PRIORITY_PUSH_TOPIC = "priority-push-topic";
  private static final String PRIORITY_PUSH_GROUP = "priority-push-group";
  private static final String PRIORITY_SIMPLE_TOPIC = "priority-simple-topic";
  private static final String PRIORITY_SIMPLE_GROUP = "priority-simple-group";

  private static final String IMAGE = "ochh/rocketmq:v5.5.1";
  private static final String ROCKETMQ_DIRECTORY = "/app/rocketmq";
  private static final int NAMESRV_PORT = 9876;
  private static final int BROKER_PORT = 10911;
  private static final int GRPC_PORT = 8081;
  private static final String NAMESRV_ADDRESS = "127.0.0.1:" + NAMESRV_PORT;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private final ClientServiceProvider provider = ClientServiceProvider.loadService();
  private final GenericContainer<?> container =
      new GenericContainer<>(DockerImageName.parse(IMAGE))
          .withWorkingDirectory(ROCKETMQ_DIRECTORY)
          .withEnv("NAMESRV_ADDR", NAMESRV_ADDRESS)
          .withEnv("NAMESRV_XMS", "64m")
          .withEnv("NAMESRV_XMX", "256m")
          .withEnv("BROKER_XMS", "128m")
          .withEnv("BROKER_XMX", "512m")
          .withEnv("BROKER_MDM", "256m")
          .withEnv("JAVA_OPT_EXT", "-XX:ActiveProcessorCount=2")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("rocketmq-modern/broker.properties"),
              "/tmp/broker.properties")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("rocketmq-modern/proxy.json"), "/tmp/proxy.json")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("rocketmq-modern/start.sh"),
              "/tmp/rocketmq-start.sh")
          .withCommand("bash", "/tmp/rocketmq-start.sh")
          .withExposedPorts(GRPC_PORT, NAMESRV_PORT, BROKER_PORT)
          .waitingFor(Wait.forLogMessage(".*rocketmq-proxy startup successfully.*\\n", 1))
          .withStartupTimeout(Duration.ofMinutes(2));

  private ClientConfiguration configuration;
  private Producer producer;
  private SimpleConsumer simpleConsumer;

  @BeforeAll
  void startBroker() throws Exception {
    cleanup.deferAfterAll(container);
    container.start();
    configuration =
        ClientConfiguration.newBuilder()
            .setEndpoints(container.getHost() + ":" + container.getMappedPort(GRPC_PORT))
            .setRequestTimeout(Duration.ofSeconds(10))
            .build();

    String brokerAddress = container.getHost() + ":" + container.getMappedPort(BROKER_PORT);
    DefaultMQAdminExt admin = new DefaultMQAdminExt();
    admin.setNamesrvAddr(container.getHost() + ":" + container.getMappedPort(NAMESRV_PORT));
    admin.setVipChannelEnabled(false);

    try {
      admin.start();

      createTopic(admin, brokerAddress, LITE_TOPIC, "+message.type=LITE,+lite.topic.expiration=60");
      createTopic(admin, brokerAddress, PRIORITY_PUSH_TOPIC, "+message.type=PRIORITY");
      createTopic(admin, brokerAddress, PRIORITY_SIMPLE_TOPIC, "+message.type=PRIORITY");

      for (String group : new String[] {LITE_GROUP, PRIORITY_PUSH_GROUP, PRIORITY_SIMPLE_GROUP}) {
        SubscriptionGroupConfig groupConfig = new SubscriptionGroupConfig();
        groupConfig.setGroupName(group);
        if (LITE_GROUP.equals(group)) {
          groupConfig.setConsumeBroadcastEnable(false);
          groupConfig.setConsumeFromMinEnable(false);
          groupConfig.setConsumeMessageOrderly(true);
          groupConfig.setAttributes(singletonMap("+lite.bind.topic", LITE_TOPIC));
        }
        admin.createAndUpdateSubscriptionGroupConfig(brokerAddress, groupConfig);
      }
    } finally {
      admin.shutdown();
    }

    producer =
        provider
            .newProducerBuilder()
            .setClientConfiguration(configuration)
            .setTopics(LITE_TOPIC, PRIORITY_PUSH_TOPIC, PRIORITY_SIMPLE_TOPIC)
            .build();
    cleanup.deferAfterAll(producer);

    simpleConsumer =
        provider
            .newSimpleConsumerBuilder()
            .setClientConfiguration(configuration)
            .setConsumerGroup(PRIORITY_SIMPLE_GROUP)
            .setSubscriptionExpressions(
                singletonMap(PRIORITY_SIMPLE_TOPIC, new FilterExpression("*")))
            .setAwaitDuration(Duration.ofSeconds(5))
            .build();
    cleanup.deferAfterAll(simpleConsumer);
  }

  @ParameterizedTest
  @MethodSource("deliveries")
  void sendsAndProcessesLiteMessages(boolean async, boolean retry) throws Exception {
    startLiteConsumer(retry);
    SendReceipt receipt = sendLiteMessage(async);

    assertPushTraces(LITE_TOPIC, LITE_GROUP, receipt, LITE_CHILD_TOPIC, null, retry ? 2 : 1);
  }

  @ParameterizedTest
  @CsvSource({"0,false", "7,true"})
  void priorityPushConsumer(int priority, boolean async) throws Exception {
    startPriorityPushConsumer();
    SendReceipt receipt = sendPriorityMessage(PRIORITY_PUSH_TOPIC, priority, async);

    assertPushTraces(PRIORITY_PUSH_TOPIC, PRIORITY_PUSH_GROUP, receipt, null, (long) priority, 1);
  }

  @ParameterizedTest
  @CsvSource({"0,false", "7,true"})
  void prioritySimpleConsumer(int priority, boolean async) throws Exception {
    SendReceipt receipt = sendPriorityMessage(PRIORITY_SIMPLE_TOPIC, priority, async);
    receivePriorityMessage(async);

    assertSimpleConsumerTraces(receipt, priority);
  }

  private static Stream<Arguments> deliveries() {
    return Stream.of(
        argumentSet("sync", false, false),
        argumentSet("async", true, false),
        argumentSet("failure then redelivery", false, true));
  }

  private void startLiteConsumer(boolean retry) throws ClientException {
    AtomicBoolean failurePending = new AtomicBoolean(retry);
    LitePushConsumer consumer =
        provider
            .newLitePushConsumerBuilder()
            .setClientConfiguration(configuration)
            .setConsumerGroup(LITE_GROUP)
            .bindTopic(LITE_TOPIC)
            .setMessageListener(
                message -> {
                  testing.runWithSpan("listener", () -> {});
                  return failurePending.getAndSet(false)
                      ? ConsumeResult.FAILURE
                      : ConsumeResult.SUCCESS;
                })
            .build();
    cleanup.deferCleanup(consumer);
    consumer.subscribeLite(LITE_CHILD_TOPIC);
  }

  private void startPriorityPushConsumer() throws ClientException {
    PushConsumer consumer =
        provider
            .newPushConsumerBuilder()
            .setClientConfiguration(configuration)
            .setConsumerGroup(PRIORITY_PUSH_GROUP)
            .setSubscriptionExpressions(
                singletonMap(PRIORITY_PUSH_TOPIC, new FilterExpression("*")))
            .setMessageListener(
                message -> {
                  testing.runWithSpan("listener", () -> {});
                  return ConsumeResult.SUCCESS;
                })
            .build();
    cleanup.deferCleanup(consumer);
  }

  private SendReceipt sendLiteMessage(boolean async) throws Exception {
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(LITE_TOPIC)
            .setLiteTopic(LITE_CHILD_TOPIC)
            .setTag("tag")
            .setKeys("key")
            .setBody("lite".getBytes(UTF_8))
            .build();
    return sendMessage(message, async);
  }

  private SendReceipt sendPriorityMessage(String topic, int priority, boolean async)
      throws Exception {
    Message message =
        provider
            .newMessageBuilder()
            .setTopic(topic)
            .setPriority(priority)
            .setTag("tag")
            .setKeys("key")
            .setBody("priority".getBytes(UTF_8))
            .build();
    return sendMessage(message, async);
  }

  private SendReceipt sendMessage(Message message, boolean async) throws Exception {
    return async ? producer.sendAsync(message).get(30, SECONDS) : producer.send(message);
  }

  private void receivePriorityMessage(boolean async) throws Exception {
    List<MessageView> messages =
        testing.runWithSpan(
            "receive parent",
            () ->
                async
                    ? simpleConsumer.receiveAsync(1, Duration.ofSeconds(30)).get(30, SECONDS)
                    : simpleConsumer.receive(1, Duration.ofSeconds(30)));

    for (MessageView message : messages) {
      simpleConsumer.ack(message);
    }
  }

  private static void createTopic(
      DefaultMQAdminExt admin, String brokerAddress, String topic, String attributes)
      throws Exception {
    TopicConfig topicConfig = new TopicConfig(topic);
    topicConfig.setReadQueueNums(8);
    topicConfig.setWriteQueueNums(8);
    topicConfig.setAttributes(AttributeParser.parseToMap(attributes));
    admin.createAndUpdateTopicConfig(brokerAddress, topicConfig);

    await()
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptionsInstanceOf(MQClientException.class)
        .untilAsserted(
            () ->
                assertThat(admin.examineTopicRouteInfo(topic).getBrokerDatas())
                    .anySatisfy(
                        broker -> assertThat(broker.getBrokerName()).isEqualTo("broker-a")));
  }

  private static SpanDataAssert assertSend(
      SpanDataAssert span, String topic, SendReceipt receipt, String liteTopic, Long priority) {
    return span.hasName("send " + topic)
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_OPERATION_NAME, "send"),
            equalTo(MESSAGING_OPERATION_TYPE, "send"),
            equalTo(MESSAGING_MESSAGE_ID, receipt.getMessageId().toString()),
            equalTo(stringKey("rocketmq.message.tag"), "tag"),
            equalTo(stringArrayKey("rocketmq.message.keys"), singletonList("key")),
            equalTo(stringKey("rocketmq.message.type"), liteTopic != null ? "lite" : "priority"),
            equalTo(stringKey("rocketmq.message.lite_topic"), liteTopic),
            equalTo(longKey("rocketmq.message.priority"), priority));
  }

  private static SpanDataAssert assertReceive(
      SpanDataAssert span,
      String topic,
      String group,
      SpanData send,
      String liteTopic,
      Long priority) {
    return span.hasName("receive " + topic)
        .hasKind(SpanKind.CLIENT)
        .hasStatus(StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_CONSUMER_GROUP_NAME, group),
            equalTo(MESSAGING_OPERATION_NAME, "receive"),
            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
            equalTo(stringKey("rocketmq.message.tag"), "tag"),
            equalTo(stringArrayKey("rocketmq.message.keys"), singletonList("key")),
            equalTo(stringKey("rocketmq.message.lite_topic"), liteTopic),
            equalTo(longKey("rocketmq.message.priority"), priority))
        .hasLinks(
            LinkData.create(
                asRemote(send.getSpanContext()),
                Attributes.of(
                    MESSAGING_MESSAGE_ID, send.getAttributes().get(MESSAGING_MESSAGE_ID))));
  }

  private static SpanDataAssert assertProcess(
      SpanDataAssert span,
      String topic,
      String group,
      SpanData send,
      String liteTopic,
      Long priority,
      boolean failure) {
    return span.hasName("process " + topic)
        .hasKind(SpanKind.CONSUMER)
        .hasParent(send)
        .hasStatus(failure ? StatusData.error() : StatusData.unset())
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("messaging.system.name"), "rocketmq"),
            equalTo(MESSAGING_DESTINATION_NAME, topic),
            equalTo(MESSAGING_CONSUMER_GROUP_NAME, group),
            equalTo(MESSAGING_OPERATION_NAME, "process"),
            equalTo(MESSAGING_OPERATION_TYPE, "process"),
            equalTo(MESSAGING_MESSAGE_ID, send.getAttributes().get(MESSAGING_MESSAGE_ID)),
            equalTo(stringKey("rocketmq.message.tag"), "tag"),
            equalTo(stringArrayKey("rocketmq.message.keys"), singletonList("key")),
            equalTo(stringKey("rocketmq.message.lite_topic"), liteTopic),
            equalTo(longKey("rocketmq.message.priority"), priority),
            equalTo(ERROR_TYPE, failure ? "FAILURE" : null))
        .hasLinks(LinkData.create(asRemote(send.getSpanContext())));
  }

  private static void assertPushTraces(
      String topic,
      String group,
      SendReceipt receipt,
      String liteTopic,
      Long priority,
      int attempts) {
    AtomicReference<SpanData> sent = new AtomicReference<>();
    List<Consumer<TraceAssert>> traces = new ArrayList<>();
    traces.add(
        trace -> {
          if (attempts == 1) {
            trace.hasSpansSatisfyingExactly(
                span -> assertSend(span, topic, receipt, liteTopic, priority),
                span ->
                    assertProcess(span, topic, group, trace.getSpan(0), liteTopic, priority, false),
                span -> span.hasName("listener").hasParent(trace.getSpan(1)));
          } else {
            trace.hasSpansSatisfyingExactly(
                span -> assertSend(span, topic, receipt, liteTopic, priority),
                span ->
                    assertProcess(span, topic, group, trace.getSpan(0), liteTopic, priority, true),
                span -> span.hasName("listener").hasParent(trace.getSpan(1)),
                span ->
                    assertProcess(span, topic, group, trace.getSpan(0), liteTopic, priority, false),
                span -> span.hasName("listener").hasParent(trace.getSpan(3)));
          }
          sent.set(trace.getSpan(0));
        });
    if (RECEIVE_TELEMETRY_ENABLED) {
      // Lite FIFO retries reuse the received message locally.
      traces.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      assertReceive(span, topic, group, sent.get(), liteTopic, priority)
                          .hasNoParent()));
    }
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.PRODUCER, SpanKind.CLIENT), traces);
    assertMessagingMetrics(topic, true);
  }

  private static void assertSimpleConsumerTraces(SendReceipt receipt, int priority) {
    AtomicReference<SpanData> sent = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> assertSend(span, PRIORITY_SIMPLE_TOPIC, receipt, null, (long) priority));
          sent.set(trace.getSpan(0));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive parent").hasNoParent(),
                span ->
                    assertReceive(
                            span,
                            PRIORITY_SIMPLE_TOPIC,
                            PRIORITY_SIMPLE_GROUP,
                            sent.get(),
                            null,
                            (long) priority)
                        .hasParent(trace.getSpan(0))));
    assertMessagingMetrics(PRIORITY_SIMPLE_TOPIC, false);
  }

  private static void assertMessagingMetrics(String topic, boolean process) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.sent.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(
                                                    stringKey("messaging.system.name"), "rocketmq"),
                                                equalTo(MESSAGING_DESTINATION_NAME, topic),
                                                equalTo(MESSAGING_OPERATION_NAME, "send"))))));
    List<String> metricNames = new ArrayList<>();
    metricNames.add("messaging.client.operation.duration");
    metricNames.add("messaging.client.consumed.messages");
    if (process) {
      metricNames.add("messaging.process.duration");
    }
    for (String metricName : metricNames) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME, metricName, metrics -> metrics.isNotEmpty());
    }
    // These assertions inspect all default metric streams, including consumer duration and
    // counts. Message-specific attributes belong to spans and links, never metric dimensions.
    assertThat(testing.metrics())
        .filteredOn(
            metric -> INSTRUMENTATION_NAME.equals(metric.getInstrumentationScopeInfo().getName()))
        .isNotEmpty()
        .allSatisfy(
            metric ->
                assertThat(metric.getData().getPoints())
                    .isNotEmpty()
                    .allSatisfy(
                        point ->
                            assertThat(point.getAttributes().asMap())
                                .doesNotContainKeys(
                                    stringKey("rocketmq.message.lite_topic"),
                                    longKey("rocketmq.message.priority"))));
  }
}
