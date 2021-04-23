/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

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
class InetSocketAddressNetRequestAttributesExtractorTest {

  InetSocketAddressNetRequestAttributesExtractor<InetSocketAddress, Void> underTest =
      new InetSocketAddressNetRequestAttributesExtractor<InetSocketAddress, Void>() {
        @Override
        protected InetSocketAddress getAddress(InetSocketAddress inetSocketAddress) {
          return inetSocketAddress;
        }

        @Override
        protected String transport(InetSocketAddress inetSocketAddress) {
          return SemanticAttributes.NetTransportValues.IP_TCP.getValue();
        }
      };

  @Test
  void noInetSocketAddress() {
    // when
    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, null);
    underTest.onEnd(attributes, null, null);

    // then
    assertThat(attributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()));
  }

  @Test
  void fullAddress() {
    // given
    InetSocketAddress address = new InetSocketAddress("github.com", 123);
    assertThat(address.getAddress().getHostAddress()).isNotNull();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, address);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, address, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()),
            entry(SemanticAttributes.NET_PEER_IP, address.getAddress().getHostAddress()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  @Test
  void unresolved() {
    // given
    InetSocketAddress address = InetSocketAddress.createUnresolved("github.com", 123);
    assertThat(address.getAddress()).isNull();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, address);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, address, null);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_TRANSPORT,
                SemanticAttributes.NetTransportValues.IP_TCP.getValue()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }
}
