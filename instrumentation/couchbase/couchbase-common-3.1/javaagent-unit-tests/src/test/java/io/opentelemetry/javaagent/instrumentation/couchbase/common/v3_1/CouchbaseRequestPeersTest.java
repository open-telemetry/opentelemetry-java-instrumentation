/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.couchbase.client.core.cnc.RequestSpan;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.Peer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1.CouchbaseRequestPeers.RequestPeerScope;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CouchbaseRequestPeersTest {

  @Test
  void unmatchedNestedRequestCannotConsumeOuterPeer() {
    RequestSpan outerParent = mock(RequestSpan.class);
    RequestSpan nestedParent = mock(RequestSpan.class);
    RequestPeerScope outer =
        CouchbaseRequestPeers.open(
            outerParent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210));
    try {
      assertThat(CouchbaseRequestPeers.open(nestedParent, null)).isNull();
      assertThat(CouchbaseRequestPeers.consume(nestedParent)).isNull();
      assertThat(CouchbaseRequestPeers.consume(null)).isNull();
      assertThat(CouchbaseRequestPeers.consume(outerParent).getPort()).isEqualTo(11210);
    } finally {
      outer.close();
    }
  }

  @Test
  void nestedScopeRestoresOuterPeer() {
    RequestSpan outerParent = mock(RequestSpan.class);
    RequestSpan nestedParent = mock(RequestSpan.class);
    RequestPeerScope outer =
        CouchbaseRequestPeers.open(
            outerParent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210));
    try {
      RequestPeerScope nested =
          CouchbaseRequestPeers.open(
              nestedParent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11211));
      try {
        assertThat(CouchbaseRequestPeers.consume(outerParent)).isNull();
        assertThat(CouchbaseRequestPeers.consume(nestedParent).getPort()).isEqualTo(11211);
      } finally {
        nested.close();
      }
      assertThat(CouchbaseRequestPeers.consume(outerParent).getPort()).isEqualTo(11210);
      assertThat(CouchbaseRequestPeers.consume(nestedParent)).isNull();
    } finally {
      outer.close();
    }
    assertThat(CouchbaseRequestPeers.consume(outerParent)).isNull();
  }

  @Test
  void exceptionalNestedExitRestoresOuterPeer() {
    RequestSpan parent = mock(RequestSpan.class);
    RequestPeerScope outer =
        CouchbaseRequestPeers.open(
            parent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210));
    try {
      assertThatThrownBy(
              () -> {
                RequestPeerScope nested =
                    CouchbaseRequestPeers.open(
                        parent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11211));
                try {
                  throw new IllegalStateException("write failed");
                } finally {
                  CouchbaseMessageHandlerInstrumentation.WriteAdvice.onExit(nested);
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(CouchbaseRequestPeers.consume(parent).getPort()).isEqualTo(11210);
    } finally {
      CouchbaseMessageHandlerInstrumentation.WriteAdvice.onExit(outer);
    }
    assertThat(CouchbaseRequestPeers.consume(parent)).isNull();
  }

  @Test
  void restoringOuterScopePreservesItsConsumption() {
    RequestSpan parent = mock(RequestSpan.class);
    RequestPeerScope outer =
        CouchbaseRequestPeers.open(
            parent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210));
    try {
      assertThat(CouchbaseRequestPeers.consume(parent)).isNotNull();
      RequestPeerScope nested =
          CouchbaseRequestPeers.open(
              parent, new InetSocketAddress(InetAddress.getLoopbackAddress(), 11211));
      nested.close();
      assertThat(CouchbaseRequestPeers.consume(parent)).isNull();
    } finally {
      outer.close();
    }
  }

  @Test
  void capturesOnlyResolvedPeerForIdenticalParent() throws UnknownHostException {

    RequestSpan parent = mock(RequestSpan.class);
    RequestSpan otherParent = mock(RequestSpan.class);
    RequestPeerScope scope =
        CouchbaseRequestPeers.open(
            parent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210));

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
  void retryCanCaptureAReplacementPeer() throws UnknownHostException {

    RequestSpan parent = mock(RequestSpan.class);
    RequestPeerScope first =
        CouchbaseRequestPeers.open(
            parent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210));
    assertThat(CouchbaseRequestPeers.consume(parent).getAddress()).isEqualTo("192.0.2.1");
    first.close();

    RequestPeerScope retry =
        CouchbaseRequestPeers.open(
            parent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 2}), 11210));
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
}
