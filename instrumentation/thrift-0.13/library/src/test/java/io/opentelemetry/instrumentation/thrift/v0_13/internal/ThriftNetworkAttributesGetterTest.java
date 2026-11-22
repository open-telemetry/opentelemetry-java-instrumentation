/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class ThriftNetworkAttributesGetterTest {

  @Test
  void preservesRawSocketAddresses() {
    SocketAddress local = mock(SocketAddress.class);
    SocketAddress peer = mock(SocketAddress.class);
    ThriftRequest request = new ThriftRequest("method", "service", local, peer, emptyMap());

    ThriftClientNetworkAttributesGetter clientGetter = new ThriftClientNetworkAttributesGetter();
    ThriftServerNetworkAttributesGetter serverGetter = new ThriftServerNetworkAttributesGetter();
    assertThat(clientGetter.getNetworkPeerSocketAddress(request, null)).isSameAs(peer);
    assertThat(serverGetter.getNetworkLocalSocketAddress(request, null)).isSameAs(local);
    assertThat(serverGetter.getNetworkPeerSocketAddress(request, null)).isSameAs(peer);
    assertThat(clientGetter.getServerAddress(request)).isNull();
    assertThat(clientGetter.getServerPort(request)).isNull();
  }

  @Test
  void keepsUnresolvedLogicalServerIdentity() {
    ThriftRequest request =
        new ThriftRequest(
            "method",
            "service",
            null,
            InetSocketAddress.createUnresolved("server.invalid", 8080),
            emptyMap());

    ThriftClientNetworkAttributesGetter getter = new ThriftClientNetworkAttributesGetter();
    assertThat(getter.getServerAddress(request)).isEqualTo("server.invalid");
    assertThat(getter.getServerPort(request)).isEqualTo(8080);
    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isEqualTo(8080);
  }
}
