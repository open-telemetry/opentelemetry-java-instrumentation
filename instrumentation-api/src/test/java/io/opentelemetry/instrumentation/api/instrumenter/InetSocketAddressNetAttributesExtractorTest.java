/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InetSocketAddressNetAttributesExtractorTest {

  @Mock private InetSocketAddressNetAttributesExtractor<Void, Void> extractor;

  @BeforeEach
  void setUp() {
    when(extractor.transport(any()))
        .thenReturn(SemanticAttributes.NetTransportValues.IP_TCP.getValue());
  }

  @Test
  void noInetSocketAddress() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, null);
    extractor.onEnd(attributes, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));
  }

  @Test
  void fullAddress() {
    InetSocketAddress address = new InetSocketAddress("github.com", 123);
    assertThat(address.getAddress().getHostAddress()).isNotNull();
    when(extractor.getAddress(any(), any())).thenReturn(address);
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));
    extractor.onEnd(attributes, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()),
            entry(SemanticAttributes.NET_PEER_IP, address.getAddress().getHostAddress()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));
  }

  @Test
  void unresolved() {
    InetSocketAddress address = InetSocketAddress.createUnresolved("github.com", 123);
    assertThat(address.getAddress()).isNull();
    when(extractor.getAddress(any(), any())).thenReturn(address);
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));
    extractor.onEnd(attributes, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));
  }
}
