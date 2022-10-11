/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InetSocketAddressNetClientAttributesGetterTest {

  private final InetSocketAddressNetClientAttributesGetter<InetSocketAddress, InetSocketAddress>
      getter =
          new InetSocketAddressNetClientAttributesGetter<InetSocketAddress, InetSocketAddress>() {
            @Override
            public String transport(InetSocketAddress request, InetSocketAddress response) {
              return SemanticAttributes.NetTransportValues.IP_TCP;
            }

            @Override
            public String peerName(InetSocketAddress request) {
              // net.peer.name and net.peer.port are tested in NetClientAttributesExtractorTest
              return null;
            }

            @Override
            public Integer peerPort(InetSocketAddress request) {
              // net.peer.name and net.peer.port are tested in NetClientAttributesExtractorTest
              return null;
            }

            @Override
            protected InetSocketAddress getPeerSocketAddress(
                InetSocketAddress request, InetSocketAddress response) {
              return response;
            }
          };
  private final NetClientAttributesExtractor<InetSocketAddress, InetSocketAddress> extractor =
      NetClientAttributesExtractor.create(getter);

  @Test
  void noInetSocketAddress() {

    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(attributes, Context.root(), null, null, null);
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP));
  }

  @Test
  void fullAddress() {
    // given
    InetSocketAddress address = new InetSocketAddress("api.github.com", 456);
    assertThat(address.getAddress().getHostAddress()).isNotNull();

    boolean ipv4 = address.getAddress() instanceof Inet4Address;

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, address);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, address, address, null);

    // then
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder builder = Attributes.builder();
    builder.put(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP);
    builder.put(NetAttributes.NET_SOCK_PEER_ADDR, address.getAddress().getHostAddress());
    if (!ipv4) {
      builder.put(NetAttributes.NET_SOCK_FAMILY, "inet6");
    }
    builder.put(NetAttributes.NET_SOCK_PEER_NAME, "api.github.com");
    builder.put(NetAttributes.NET_SOCK_PEER_PORT, 456L);

    assertThat(endAttributes.build()).isEqualTo(builder.build());
  }

  @Test
  void unresolved() {
    // given
    InetSocketAddress address = InetSocketAddress.createUnresolved("api.github.com", 456);
    assertThat(address.getAddress()).isNull();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, address);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, address, address, null);

    // then
    assertThat(startAttributes.build()).isEmpty();

    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP));
  }
}
