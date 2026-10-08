/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.asRemote;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_SUBSCRIPTION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.client.impl.ConsumerBase;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;
import org.junit.jupiter.api.Test;

class PulsarClientSuppressReceiveSpansTest extends AbstractPulsarClientTest {

  @Test
  void testFailedListenerCountsConsumedMessage() throws Exception {
    String topic = "persistent://public/default/testFailedListenerCountsConsumedMessage";
    CountDownLatch latch = new CountDownLatch(1);
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      latch.countDown();
                      throw new IllegalStateException("test");
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();
    testing.runWithSpan("parent", () -> producer.send("test"));

    assertThat(latch.await(1, MINUTES)).isTrue();

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
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
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "pulsar"),
                                                equalTo(MESSAGING_DESTINATION_NAME, topic),
                                                equalTo(
                                                    MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                    "test_sub"),
                                                equalTo(
                                                    ERROR_TYPE,
                                                    IllegalStateException.class.getName()))))));
  }

  @Test
  void testConsumeNonPartitionedTopic() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopic";
    CountDownLatch latch = new CountDownLatch(1);
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    latch.await(1, MINUTES);

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
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "pulsar"),
                                                equalTo(MESSAGING_DESTINATION_NAME, topic),
                                                equalTo(
                                                    MESSAGING_DESTINATION_SUBSCRIPTION_NAME,
                                                    "test_sub"))))));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(topic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic, msgId.toString(), false)),
                span ->
                    span.hasName("process " + destinationName(topic))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic, msgId.toString(), false))));
  }

  @Test
  void testConsumeNonPartitionedTopicUsingReceive() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceive";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    Message<String> receivedMsg = consumer.receive();
    consumer.acknowledge(receivedMsg);

    assertDirectReceiveTraces(topic, msgId);
  }

  @Test
  void testConsumeNonPartitionedTopicUsingReceiveAsync() throws Exception {
    String topic = "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveAsync";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    CompletableFuture<Message<String>> result =
        consumer
            .receiveAsync()
            .whenComplete(
                (message, throwable) -> {
                  if (message != null) {
                    acknowledgeMessage(consumer, message);
                  }
                });

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    result.get(1, MINUTES);

    assertDirectReceiveTraces(topic, msgId);
  }

  @Test
  void testConsumeNonPartitionedTopicUsingReceiveWithTimeout() throws Exception {
    String topic =
        "persistent://public/default/testConsumeNonPartitionedTopicCallReceiveWithTimeout";
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    Message<String> receivedMsg = consumer.receive(1, MINUTES);
    consumer.acknowledge(receivedMsg);

    assertDirectReceiveTraces(topic, msgId);
  }

  private static void assertDirectReceiveTraces(String topic, MessageId msgId) {

    AtomicReference<SpanData> producerSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span ->
                  span.hasName("send " + destinationName(topic))
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          sendAttributes(topic, msgId.toString(), false)));
          producerSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("receive " + destinationName(topic))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasLinks(LinkData.create(asRemote(producerSpan.get().getSpanContext())))
                        .hasAttributesSatisfyingExactly(
                            receiveAttributes(topic, msgId.toString(), false))));
  }

  @Test
  void captureMessageHeaderAsSpanAttribute() throws Exception {
    String topic = "persistent://public/default/testCaptureMessageHeaderTopic";
    CountDownLatch latch = new CountDownLatch(1);
    admin.topics().createNonPartitionedTopic(topic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(topic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId =
        testing.runWithSpan(
            "parent",
            () ->
                producer
                    .newMessage()
                    .value(msg)
                    .property("Test-Message-Header", "test")
                    .property("Uncaptured-Header", "password")
                    .send());

    latch.await(1, MINUTES);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(topic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic, msgId.toString(), true)),
                span ->
                    span.hasName("process " + destinationName(topic))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic, msgId.toString(), true))));
  }

  @Test
  void testConsumePartitionedTopic() throws Exception {
    String topic = "persistent://public/default/testConsumePartitionedTopic";
    admin.topics().createPartitionedTopic(topic, 1);
    CountDownLatch latch = new CountDownLatch(1);

    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .topic(topic)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    producer = client.newProducer(Schema.STRING).topic(topic).enableBatching(false).create();

    String msg = "test";
    MessageId msgId = testing.runWithSpan("parent", () -> producer.send(msg));

    latch.await(1, MINUTES);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(topic + "-partition-0"))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic + "-partition-0", msgId.toString(), false)),
                span ->
                    span.hasName("process " + destinationName(topic + "-partition-0"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic + "-partition-0", msgId.toString(), false))));
  }

  @Test
  void nestedListenerDoesNotEmitReceiveSpan() throws Exception {
    String innerTopic = "persistent://public/default/nestedListenerInner";
    String receiveTopic = "persistent://public/default/nestedListenerReceive";
    admin.topics().createNonPartitionedTopic(innerTopic);
    admin.topics().createNonPartitionedTopic(receiveTopic);
    consumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(innerTopic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    Consumer<String> receiveConsumer =
        client
            .newConsumer(Schema.STRING)
            .subscriptionName("test_sub")
            .topic(receiveTopic)
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .subscribe();
    producer = client.newProducer(Schema.STRING).topic(receiveTopic).enableBatching(false).create();
    producer2 = client.newProducer(Schema.STRING).topic(innerTopic).enableBatching(false).create();
    MessageId innerMessageId = testing.runWithSpan("inner-parent", () -> producer2.send("inner"));
    MessageId receiveMessageId =
        testing.runWithSpan("receive-parent", () -> producer.send("receive"));

    Queue<Message<String>> innerMessages = new ArrayDeque<>();
    ConsumerBase<String> innerDispatcher =
        listenerDispatcher(innerMessages, (unused1, unused2) -> {}, false);

    AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
    Message<String> outerMessage = listenerMessage("outer-topic", MessageId.latest);
    Queue<Message<String>> outerMessages = new ArrayDeque<>();
    outerMessages.add(outerMessage);
    ConsumerBase<String> outerDispatcher =
        listenerDispatcher(
            outerMessages,
            (unused1, unused2) -> {
              try {
                try (Scope ignored = Context.root().makeCurrent()) {
                  innerMessages.add(consumer.receiveAsync().join());
                  triggerListener(innerDispatcher);
                }
                Message<String> received = receiveConsumer.receiveAsync().join();
                acknowledgeMessage(receiveConsumer, received);
              } catch (Throwable t) {
                listenerFailure.set(t);
              }
            },
            true);

    try {
      triggerListener(outerDispatcher);
    } finally {
      receiveConsumer.close();
    }

    assertThat(listenerFailure.get()).isNull();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName(
            "inner-parent", "process " + destinationName("outer-topic"), "receive-parent"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("inner-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(innerTopic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(innerTopic, innerMessageId.toString(), false))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("process " + destinationName("outer-topic"))
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            processAttributes("outer-topic", MessageId.latest.toString(), false))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("receive-parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(receiveTopic))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(receiveTopic, receiveMessageId.toString(), false))));
  }

  @Test
  void testConsumeMultiTopics() throws Exception {
    String topicNamePrefix = "persistent://public/default/testConsumeMulti_";
    String topic1 = topicNamePrefix + "1";
    String topic2 = topicNamePrefix + "2";
    CountDownLatch latch = new CountDownLatch(2);
    producer = client.newProducer(Schema.STRING).topic(topic1).enableBatching(false).create();
    producer2 = client.newProducer(Schema.STRING).topic(topic2).enableBatching(false).create();

    MessageId msgId1 = testing.runWithSpan("parent1", () -> producer.send("test1"));
    MessageId msgId2 = testing.runWithSpan("parent2", () -> producer2.send("test2"));

    consumer =
        client
            .newConsumer(Schema.STRING)
            .topic(topic2, topic1)
            .subscriptionName("test_sub")
            .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
            .messageListener(
                (MessageListener<String>)
                    (consumer, msg) -> {
                      acknowledgeMessage(consumer, msg);
                      latch.countDown();
                    })
            .subscribe();

    latch.await(1, MINUTES);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent1", "parent2"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent1").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(topic1))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic1, msgId1.toString(), false)),
                span ->
                    span.hasName("process " + destinationName(topic1))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic1, msgId1.toString(), false))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent2").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("send " + destinationName(topic2))
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            sendAttributes(topic2, msgId2.toString(), false)),
                span ->
                    span.hasName("process " + destinationName(topic2))
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            processAttributes(topic2, msgId2.toString(), false))));
  }

  @SuppressWarnings("unchecked")
  private static Message<String> listenerMessage(String topic, MessageId messageId) {
    Message<String> message = mock(Message.class);
    when(message.getTopicName()).thenReturn(topic);
    when(message.getMessageId()).thenReturn(messageId);
    when(message.getProperties()).thenReturn(emptyMap());
    return message;
  }

  @SuppressWarnings("unchecked")
  private static ConsumerBase<String> listenerDispatcher(
      Queue<Message<String>> messages, MessageListener<String> listener, boolean instrumentListener)
      throws ReflectiveOperationException {
    AtomicReference<MessageListener<String>> listenerReference = new AtomicReference<>();
    ConsumerBase<String> dispatcher =
        mock(
            ConsumerBase.class,
            withSettings()
                .defaultAnswer(
                    invocation -> {
                      if (invocation.getMethod().getName().equals("internalReceive")
                          && invocation.getArguments().length == 2) {
                        return messages.poll();
                      }
                      if (invocation.getMethod().getName().equals("callMessageListener")) {
                        listenerReference
                            .get()
                            .received(
                                (Consumer<String>) invocation.getMock(),
                                (Message<String>) invocation.getArgument(0));
                        return null;
                      }
                      return invocation.callRealMethod();
                    }));
    ConsumerConfigurationData<String> conf = new ConsumerConfigurationData<>();
    conf.setMessageListener(listener);
    ScheduledThreadPoolExecutor directExecutor =
        new ScheduledThreadPoolExecutor(1) {
          @Override
          public void execute(Runnable command) {
            command.run();
          }
        };
    directExecutor.shutdown();
    MessageListener<String> wrappedListener =
        instrumentListener ? conf.getMessageListener() : listener;
    listenerReference.set(wrappedListener);
    setField(dispatcher, "conf", conf);
    setField(dispatcher, "listener", wrappedListener);
    try {
      setField(dispatcher, "pinnedExecutor", directExecutor);
    } catch (NoSuchFieldException ignored) {
      try {
        setField(dispatcher, "internalPinnedExecutor", directExecutor);
        Field messageListenerExecutor =
            ConsumerBase.class.getDeclaredField("messageListenerExecutor");
        Object executor =
            Proxy.newProxyInstance(
                messageListenerExecutor.getType().getClassLoader(),
                new Class<?>[] {messageListenerExecutor.getType()},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("execute")) {
                    ((Runnable) arguments[1]).run();
                  }
                  return null;
                });
        messageListenerExecutor.setAccessible(true);
        messageListenerExecutor.set(dispatcher, executor);
      } catch (NoSuchFieldException ignoredAgain) {
        Field listenerTaskScheduler = ConsumerBase.class.getDeclaredField("listenerTaskScheduler");
        Object scheduler =
            mock(
                listenerTaskScheduler.getType(),
                invocation -> {
                  if (invocation.getMethod().getName().equals("trigger")) {
                    wrappedListener.received(dispatcher, messages.poll());
                  }
                  return null;
                });
        listenerTaskScheduler.setAccessible(true);
        listenerTaskScheduler.set(dispatcher, scheduler);
      }
    }
    setField(dispatcher, "subscription", "test_sub");
    return dispatcher;
  }

  private static void setField(Object target, String name, Object value)
      throws ReflectiveOperationException {
    Field field = ConsumerBase.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static void triggerListener(ConsumerBase<String> dispatcher) {
    try {
      Method method;
      try {
        // added in 4.2.5
        method = ConsumerBase.class.getDeclaredMethod("drainListener");
      } catch (NoSuchMethodException ignored) {
        method = ConsumerBase.class.getDeclaredMethod("triggerListener");
      }
      method.setAccessible(true);
      method.invoke(dispatcher);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
    }
  }
}
