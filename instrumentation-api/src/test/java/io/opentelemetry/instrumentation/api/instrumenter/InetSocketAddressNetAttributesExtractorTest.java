/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InetSocketAddressNetAttributesExtractorTest {

  private final InetSocketAddressNetAttributesExtractor<InetSocketAddress, Void> extractor =
      new InetSocketAddressNetAttributesExtractor<InetSocketAddress, Void>() {
        @Override
        protected InetSocketAddress getAddress(InetSocketAddress inetSocketAddress, Void unused) {
          return inetSocketAddress;
        }

        @Override
        protected String transport(InetSocketAddress inetSocketAddress) {
          return SemanticAttributes.NetTransportValues.IP_TCP.getValue();
        }
      };

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

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, address);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));

    extractor.onEnd(attributes, address, null);
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

    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, address);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));

    extractor.onEnd(attributes, address, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));
  }
}
