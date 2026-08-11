/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import apache.rocketmq.v2.MessageQueue;
import apache.rocketmq.v2.ReceiveMessageRequest;
import apache.rocketmq.v2.Resource;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0.ReceiveSpanFinishingCallback;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqClientTest extends AbstractRocketMqClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void testFailedPushReceiveWithReceiveTelemetryEnabled() {
    ReceiveMessageRequest request = request();
    IllegalStateException error = new IllegalStateException("test");

    new ReceiveSpanFinishingCallback(request, Timer.start(), false, true).onFailure(error);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "receive test-topic"
                                : "test-topic receive")
                        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(error)));
  }

  @Test
  void testFailedPushReceiveWithReceiveTelemetryDisabled() {
    ReceiveMessageRequest request = request();

    new ReceiveSpanFinishingCallback(request, Timer.start(), false, false)
        .onFailure(new IllegalStateException("test"));

    assertThat(testing.spans()).isEmpty();
  }

  private static ReceiveMessageRequest request() {
    Resource topic = mock(Resource.class);
    when(topic.getName()).thenReturn("test-topic");
    Resource group = mock(Resource.class);
    when(group.getName()).thenReturn("test-group");
    MessageQueue messageQueue = mock(MessageQueue.class);
    when(messageQueue.getTopic()).thenReturn(topic);
    ReceiveMessageRequest request = mock(ReceiveMessageRequest.class);
    when(request.getMessageQueue()).thenReturn(messageQueue);
    when(request.getGroup()).thenReturn(group);
    return request;
  }
}
