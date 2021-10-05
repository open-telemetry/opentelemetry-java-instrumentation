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
class InetSocketAddressNetServerAttributesExtractorTest {

  private final InetSocketAddressNetServerAttributesExtractor<InetSocketAddress, InetSocketAddress>
      extractor =
          new InetSocketAddressNetServerAttributesExtractor<
              InetSocketAddress, InetSocketAddress>() {
            @Override
            public InetSocketAddress getAddress(InetSocketAddress request) {
              return request;
            }

            @Override
            public String transport(InetSocketAddress request) {
              return SemanticAttributes.NetTransportValues.IP_TCP;
            }
          };

  @Test
  void noInetSocketAddress() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP));
  }

  @Test
  void fullAddress() {
    // given
    InetSocketAddress address = new InetSocketAddress("github.com", 123);
    assertThat(address.getAddress().getHostAddress()).isNotNull();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, address);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP),
            entry(SemanticAttributes.NET_PEER_IP, address.getAddress().getHostAddress()),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));
  }

  @Test
  void unresolved() {
    // given
    InetSocketAddress address = InetSocketAddress.createUnresolved("github.com", 123);
    assertThat(address.getAddress()).isNull();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, address);

    // then
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP),
            entry(SemanticAttributes.NET_PEER_NAME, "github.com"),
            entry(SemanticAttributes.NET_PEER_PORT, 123L));
  }
}
