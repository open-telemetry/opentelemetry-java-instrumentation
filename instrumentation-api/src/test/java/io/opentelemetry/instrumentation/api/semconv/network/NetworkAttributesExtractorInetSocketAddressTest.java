/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.NetworkAttributes;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class NetworkAttributesExtractorInetSocketAddressTest {

  static class TestNetworkAttributesGetter
      implements NetworkAttributesGetter<InetSocketAddress, InetSocketAddress> {

    @Nullable
    @Override
    public InetSocketAddress getNetworkLocalInetSocketAddress(
        InetSocketAddress request, @Nullable InetSocketAddress response) {
      return request;
    }

    @Nullable
    @Override
    public InetSocketAddress getNetworkPeerInetSocketAddress(
        InetSocketAddress request, @Nullable InetSocketAddress response) {
      return response;
    }
  }

  @Test
  void fullAddress() {
    InetSocketAddress local = new InetSocketAddress("1.2.3.4", 8080);
    InetSocketAddress peer = new InetSocketAddress("4.3.2.1", 9090);

    AttributesExtractor<InetSocketAddress, InetSocketAddress> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), local);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), local, peer, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(NetworkAttributes.NETWORK_TYPE, "ipv4"),
            entry(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "1.2.3.4"),
            entry(NetworkAttributes.NETWORK_LOCAL_PORT, 8080L),
            entry(NetworkAttributes.NETWORK_PEER_ADDRESS, "4.3.2.1"),
            entry(NetworkAttributes.NETWORK_PEER_PORT, 9090L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<InetSocketAddress, InetSocketAddress> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), null);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), null, null, null);
    assertThat(endAttributes.build()).isEmpty();
  }
}
