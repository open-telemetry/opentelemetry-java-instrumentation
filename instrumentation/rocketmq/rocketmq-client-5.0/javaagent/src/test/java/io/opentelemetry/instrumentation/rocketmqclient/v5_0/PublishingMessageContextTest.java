/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.MessageType;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;
import org.apache.rocketmq.client.java.message.protocol.Resource;
import org.apache.rocketmq.client.java.route.MessageQueueImpl;
import org.apache.rocketmq.shaded.com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PublishingMessageContextTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void shouldPreserveSubmissionContextForDeferredSend() throws Exception {
    Message message =
        ClientServiceProvider.loadService()
            .newMessageBuilder()
            .setTopic("context-capture")
            .setBody(new byte[] {1})
            .build();
    // The real constructor takes ProducerSettings in 5.0.0 and PublishingSettings in later clients.
    Constructor<?> constructor = PublishingMessageImpl.class.getConstructors()[0];
    Object settings =
        mock(
            constructor.getParameterTypes()[1],
            invocation -> {
              if (invocation.getMethod().getName().equals("getMaxBodySizeBytes")) {
                return 1024;
              }
              if (invocation.getMethod().getName().equals("isValidateMessageType")) {
                return true;
              }
              return RETURNS_DEFAULTS.answer(invocation);
            });
    PublishingMessageImpl publishingMessage =
        testing.runWithSpan(
            "submission",
            () -> (PublishingMessageImpl) constructor.newInstance(message, settings, false));

    Class<?> producerClass =
        Class.forName("org.apache.rocketmq.client.java.impl.producer.ProducerImpl");
    Object producer = mock(producerClass);
    Field settingsField =
        Arrays.stream(producerClass.getDeclaredFields())
            .filter(field -> field.getType() == constructor.getParameterTypes()[1])
            .findFirst()
            .get();
    settingsField.setAccessible(true);
    settingsField.set(producer, settings);
    // Reject the message type in the real send method before any broker interaction.
    MessageQueueImpl queue = mock(MessageQueueImpl.class);
    when(queue.getTopicResource()).thenReturn(new Resource(message.getTopic()));
    when(queue.getAcceptMessageTypes()).thenReturn(emptyList());
    Method send =
        producerClass.getDeclaredMethod(
            "send0",
            SettableFuture.class,
            String.class,
            MessageType.class,
            List.class,
            List.class,
            int.class);
    send.setAccessible(true);
    SettableFuture<List<SendReceiptImpl>> future = SettableFuture.create();
    testing.runWithSpan(
        "later-ambient",
        () ->
            send.invoke(
                producer,
                future,
                message.getTopic(),
                publishingMessage.getMessageType(),
                singletonList(queue),
                singletonList(publishingMessage),
                1));
    Throwable sendFailure = catchThrowableOfType(ExecutionException.class, future::get).getCause();
    assertThat(sendFailure).isInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("submission").hasNoParent(),
                span ->
                    span.hasName("send context-capture")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasException(sendFailure)),
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("later-ambient").hasNoParent()));
  }
}
