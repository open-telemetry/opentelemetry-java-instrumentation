/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.couchbase.client.core.cnc.RequestSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Peer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Scope;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CouchbaseRequestPeersTest {

  @Test
  void capturesOnlyResolvedPeerForIdenticalParent() throws UnknownHostException {
    RequestSpan parent = mock(RequestSpan.class);
    RequestSpan otherParent = mock(RequestSpan.class);
    Scope scope = CouchbaseRequestPeers.open(parent, resolvedAddress("192.0.2.1", 11210));

    assertThat(scope).isNotNull();
    assertThat(CouchbaseRequestPeers.consume(otherParent)).isNull();
    Peer peer = CouchbaseRequestPeers.consume(parent);
    assertThat(peer.getAddress()).isEqualTo("192.0.2.1");
    assertThat(peer.getPort()).isEqualTo(11210);
    assertThat(CouchbaseRequestPeers.consume(parent)).isNull();

    scope.close();
    assertThat(CouchbaseRequestPeers.consume(parent)).isNull();
  }

  @Test
  void nestedScopesKeepPeerBoundToParentIdentity() throws UnknownHostException {
    RequestSpan outerParent = mock(RequestSpan.class);
    RequestSpan innerParent = mock(RequestSpan.class);
    Scope outer = CouchbaseRequestPeers.open(outerParent, resolvedAddress("192.0.2.1", 11210));
    Scope inner = CouchbaseRequestPeers.open(innerParent, resolvedAddress("192.0.2.2", 8093));

    assertThat(CouchbaseRequestPeers.consume(outerParent)).isNull();
    assertThat(CouchbaseRequestPeers.consume(innerParent).getAddress()).isEqualTo("192.0.2.2");
    inner.close();
    assertThat(CouchbaseRequestPeers.consume(outerParent).getAddress()).isEqualTo("192.0.2.1");
    outer.close();
  }

  @Test
  void retryCanCaptureAReplacementPeer() throws UnknownHostException {
    RequestSpan parent = mock(RequestSpan.class);
    Scope first = CouchbaseRequestPeers.open(parent, resolvedAddress("192.0.2.1", 11210));
    assertThat(CouchbaseRequestPeers.consume(parent).getAddress()).isEqualTo("192.0.2.1");
    first.close();

    Scope retry = CouchbaseRequestPeers.open(parent, resolvedAddress("192.0.2.2", 11210));
    assertThat(CouchbaseRequestPeers.consume(parent).getAddress()).isEqualTo("192.0.2.2");
    retry.close();
  }

  @Test
  void unresolvedOrUnsupportedAddressesAreOmitted() {
    RequestSpan parent = mock(RequestSpan.class);

    assertThat(
            CouchbaseRequestPeers.open(
                parent, InetSocketAddress.createUnresolved("db.example", 11210)))
        .isNull();
    assertThat(CouchbaseRequestPeers.open(parent, null)).isNull();
    assertThat(CouchbaseRequestPeers.open(null, new InetSocketAddress(11210))).isNull();
  }

  private static InetSocketAddress resolvedAddress(String address, int port)
      throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(address, parseAddress(address)), port);
  }

  private static byte[] parseAddress(String address) {
    String[] octets = address.split("\\.");
    byte[] result = new byte[octets.length];
    for (int i = 0; i < octets.length; i++) {
      result[i] = (byte) Integer.parseInt(octets[i]);
    }
    return result;
  }
}
