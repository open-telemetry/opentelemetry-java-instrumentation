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
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("deprecation") // testing deprecated class
@ExtendWith(MockitoExtension.class)
class InetSocketAddressNetServerAttributesGetterTest {

  static class TestNetServerAttributesGetter
      implements NetServerAttributesGetter<Addresses, Addresses> {

    @Override
    public String getServerAddress(Addresses request) {
      // net.host.name and net.host.port are tested in NetClientAttributesExtractorTest
      return null;
    }

    @Override
    public Integer getServerPort(Addresses request) {
      // net.host.name and net.host.port are tested in NetClientAttributesExtractorTest
      return null;
    }

    @Override
    public InetSocketAddress getNetworkPeerInetSocketAddress(
        Addresses request, Addresses response) {
      return request.peer;
    }

    @Override
    public InetSocketAddress getNetworkLocalInetSocketAddress(
        Addresses request, Addresses response) {
      return request.host;
    }
  }

  private final AttributesExtractor<Addresses, Addresses> extractor =
      NetServerAttributesExtractor.create(new TestNetServerAttributesGetter());

  @Test
  void noInetSocketAddress() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), new Addresses(null, null));
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  @SuppressWarnings("AddressSelection")
  void fullAddress() {
    // given
    Addresses request =
        new Addresses(
            new InetSocketAddress("github.com", 123), new InetSocketAddress("api.github.com", 456));
    assertThat(request.peer.getAddress().getHostAddress()).isNotNull();
    assertThat(request.host.getAddress().getHostAddress()).isNotNull();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, request, request, null);

    // then
    if (!request.isIpv4()) {
      assertThat(startAttributes.build())
          .isEqualTo(Attributes.of(SemanticAttributes.NET_SOCK_FAMILY, "inet6"));
    } else {
      assertThat(startAttributes.build()).isEmpty();
    }

    assertThat(endAttributes.build())
        .containsOnly(
            entry(
                SemanticAttributes.NET_SOCK_HOST_ADDR, request.host.getAddress().getHostAddress()),
            entry(SemanticAttributes.NET_SOCK_HOST_PORT, 456L),
            entry(
                SemanticAttributes.NET_SOCK_PEER_ADDR, request.peer.getAddress().getHostAddress()),
            entry(SemanticAttributes.NET_SOCK_PEER_PORT, 123L));
  }

  @Test
  void unresolved() {
    // given
    Addresses request =
        new Addresses(
            InetSocketAddress.createUnresolved("github.com", 123),
            InetSocketAddress.createUnresolved("api.github.com", 456));
    assertThat(request.peer.getAddress()).isNull();
    assertThat(request.host.getAddress()).isNull();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, context, request, request, null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(endAttributes.build()).isEmpty();
  }

  static final class Addresses {

    private final InetSocketAddress peer;
    private final InetSocketAddress host;

    Addresses(InetSocketAddress peer, InetSocketAddress host) {
      this.peer = peer;
      this.host = host;
    }

    boolean isIpv4() {
      return peer.getAddress() instanceof Inet4Address;
    }
  }
}
