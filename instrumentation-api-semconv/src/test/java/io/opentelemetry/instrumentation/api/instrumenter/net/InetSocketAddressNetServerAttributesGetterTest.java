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
class InetSocketAddressNetServerAttributesGetterTest {

  final InetSocketAddressNetServerAttributesGetter<Addresses> getter =
      new InetSocketAddressNetServerAttributesGetter<Addresses>() {

        @Override
        public String transport(Addresses request) {
          return SemanticAttributes.NetTransportValues.IP_TCP;
        }

        @Override
        public String hostName(Addresses request) {
          // net.host.name and net.host.port are tested in NetClientAttributesExtractorTest
          return null;
        }

        @Override
        public Integer hostPort(Addresses request) {
          // net.host.name and net.host.port are tested in NetClientAttributesExtractorTest
          return null;
        }

        @Override
        protected InetSocketAddress getPeerSocketAddress(Addresses request) {
          return request.peer;
        }

        @Override
        protected InetSocketAddress getHostSocketAddress(Addresses request) {
          return request.host;
        }
      };
  private final NetServerAttributesExtractor<Addresses, Addresses> extractor =
      NetServerAttributesExtractor.create(getter);

  @Test
  void noInetSocketAddress() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), new Addresses(null, null));
    assertThat(attributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP));
  }

  @Test
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
    AttributesBuilder builder = Attributes.builder();
    builder.put(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP);
    if (!request.isIpv4()) {
      builder.put(NetAttributes.NET_SOCK_FAMILY, "inet6");
    }
    builder.put(NetAttributes.NET_SOCK_PEER_ADDR, request.peer.getAddress().getHostAddress());
    builder.put(NetAttributes.NET_SOCK_PEER_PORT, 123L);
    builder.put(NetAttributes.NET_SOCK_HOST_ADDR, request.host.getAddress().getHostAddress());
    builder.put(NetAttributes.NET_SOCK_HOST_PORT, 456L);

    assertThat(startAttributes.build()).isEqualTo(builder.build());

    assertThat(endAttributes.build()).isEmpty();
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
    assertThat(startAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NET_TRANSPORT, SemanticAttributes.NetTransportValues.IP_TCP));

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
