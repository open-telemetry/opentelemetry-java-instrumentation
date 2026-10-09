/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TracingSendMessageHookImplTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void propagatesContextInEncodedBatch(int messageCount) throws Exception {
    List<Message> messages = new ArrayList<>();
    for (int i = 0; i < messageCount; i++) {
      Message message = new Message("topic", ("body-" + i).getBytes(UTF_8));
      message.setFlag(i);
      message.putUserProperty("custom", "value-" + i);
      messages.add(message);
    }
    MessageBatch batch = MessageBatch.generateFromList(messages);
    batch.setBody(batch.encode());
    SendMessageContext request = new SendMessageContext();
    request.setMessage(batch);
    request.setCommunicationMode(CommunicationMode.ONEWAY);
    SendMessageHook hook =
        RocketMqTelemetry.create(testing.getOpenTelemetry()).createSendMessageHook();

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          hook.sendMessageAfter(request);
        });

    List<Message> decoded = MessageDecoder.decodeMessages(ByteBuffer.wrap(batch.getBody()));
    assertThat(decoded).hasSize(messageCount);
    for (int i = 0; i < messageCount; i++) {
      Message message = decoded.get(i);
      assertThat(message.getProperty("traceparent"))
          .isNotNull()
          .isEqualTo(batch.getProperty("traceparent"));
      assertThat(message.getProperty("custom")).isEqualTo("value-" + i);
      assertThat(message.getBody()).isEqualTo(messages.get(i).getBody());
      assertThat(message.getFlag()).isEqualTo(i);
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName("send topic")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))));
  }
}
