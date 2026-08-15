/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RocketMqTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    RocketMqTelemetry telemetry =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("*"))
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    RocketMqTelemetry telemetry =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(IncludeExclude.builder().setIncluded("*").build())
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isNotEmpty();
  }

  private static List<String> capturedHeaderKeys(RocketMqTelemetry telemetry) {
    Message message = new Message("topic", new byte[0]);
    message.putUserProperty("Test-Message-Header", "test");
    SendMessageContext context = new SendMessageContext();
    context.setMessage(message);
    context.setCommunicationMode(CommunicationMode.ONEWAY);

    SendMessageHook hook = telemetry.createSendMessageHook();
    hook.sendMessageBefore(context);
    hook.sendMessageAfter(context);

    List<SpanData> spans = testing.waitForTraces(1).get(0);
    Attributes attributes = spans.get(0).getAttributes();
    return attributes.asMap().keySet().stream()
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
