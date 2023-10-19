/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("deprecation") // testing deprecated class
@ExtendWith(MockitoExtension.class)
class InetSocketAddressNetClientAttributesGetterTest {

  static class TestNetClientAttributesGetter
      implements NetClientAttributesGetter<InetSocketAddress, InetSocketAddress> {

    @Override
    public String getServerAddress(InetSocketAddress request) {
      // net.peer.name and net.peer.port are tested in NetClientAttributesExtractorTest
      return null;
    }

    @Override
    public Integer getServerPort(InetSocketAddress request) {
      // net.peer.name and net.peer.port are tested in NetClientAttributesExtractorTest
      return null;
    }

    @Override
    public InetSocketAddress getNetworkPeerInetSocketAddress(
        InetSocketAddress request, InetSocketAddress response) {
      return response;
    }
  }

  private final AttributesExtractor<InetSocketAddress, InetSocketAddress> extractor =
      NetClientAttributesExtractor.create(new TestNetClientAttributesGetter());

  @Test
  void noInetSocketAddress() {

    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(attributes, Context.root(), null, null, null);
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  @SuppressWarnings("AddressSelection")
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
    builder.put(SemanticAttributes.NET_SOCK_PEER_ADDR, address.getAddress().getHostAddress());
    if (!ipv4) {
      builder.put(SemanticAttributes.NET_SOCK_FAMILY, "inet6");
    }
    builder.put(SemanticAttributes.NET_SOCK_PEER_NAME, "api.github.com");
    builder.put(SemanticAttributes.NET_SOCK_PEER_PORT, 456L);

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
    assertThat(endAttributes.build()).isEmpty();
  }
}
