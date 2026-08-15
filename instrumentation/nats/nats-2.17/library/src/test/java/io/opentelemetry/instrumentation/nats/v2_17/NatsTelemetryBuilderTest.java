/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.nats.client.Connection;
import io.nats.client.Options;
import io.nats.client.api.ServerInfo;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NatsTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    NatsTelemetry telemetry =
        NatsTelemetry.builder(testing.getOpenTelemetry())
            .setCapturedHeaders(singletonList("*"))
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    NatsTelemetry telemetry =
        NatsTelemetry.builder(testing.getOpenTelemetry())
            .setHeaders(IncludeExclude.builder().setIncluded("*").build())
            .build();

    assertThat(capturedHeaderKeys(telemetry)).isNotEmpty();
  }

  private static List<String> capturedHeaderKeys(NatsTelemetry telemetry) {
    Headers headers = new Headers();
    headers.add("Test-Message-Header", "test");

    Connection delegate = mock(Connection.class);
    when(delegate.getServerInfo()).thenReturn(mock(ServerInfo.class));
    when(delegate.getOptions()).thenReturn(new Options.Builder().build());

    Connection connection = telemetry.wrap(delegate);
    connection.publish("subject", headers, "body".getBytes(UTF_8));

    List<SpanData> spans = testing.waitForTraces(1).get(0);
    return spans.get(0).getAttributes().asMap().keySet().stream()
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
