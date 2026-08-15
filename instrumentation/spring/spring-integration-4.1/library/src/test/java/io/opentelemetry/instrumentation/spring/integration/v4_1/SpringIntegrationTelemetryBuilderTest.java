/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;

class SpringIntegrationTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    SpringIntegrationTelemetry telemetry =
        SpringIntegrationTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("*"))
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    SpringIntegrationTelemetry telemetry =
        SpringIntegrationTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(IncludeExclude.builder().setIncluded("*").build())
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isNotEmpty();
  }

  private static List<String> capturedHeaderKeys(SpringIntegrationTelemetry telemetry) {
    Message<String> message =
        new GenericMessage<>("body", singletonMap("Test-Message-Header", "test"));
    MessageChannel channel = (m, timeout) -> true;

    ChannelInterceptor interceptor = telemetry.createChannelInterceptor();
    Message<?> sent = interceptor.preSend(message, channel);
    interceptor.afterSendCompletion(sent, channel, true, null);

    List<SpanData> spans = testing.waitForTraces(1).get(0);
    return spans.get(0).getAttributes().asMap().keySet().stream()
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
