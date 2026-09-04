/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan parent = mock(RequestSpan.class);
    RequestSpan otherParent = mock(RequestSpan.class);
    Scope scope =
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
  void nestedScopesKeepPeerBoundToParentIdentity() throws UnknownHostException {
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan outerParent = mock(RequestSpan.class);
    RequestSpan innerParent = mock(RequestSpan.class);
    Scope outer =
        CouchbaseRequestPeers.open(
            outerParent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210));
    Scope inner =
        CouchbaseRequestPeers.open(
            innerParent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 2}), 8093));

    assertThat(CouchbaseRequestPeers.consume(outerParent)).isNull();
    assertThat(CouchbaseRequestPeers.consume(innerParent).getAddress()).isEqualTo("192.0.2.2");
    inner.close();
    assertThat(CouchbaseRequestPeers.consume(outerParent).getAddress()).isEqualTo("192.0.2.1");
    outer.close();
  }

  @Test
  void retryCanCaptureAReplacementPeer() throws UnknownHostException {
    assumeTrue(emitStableDatabaseSemconv());
    RequestSpan parent = mock(RequestSpan.class);
    Scope first =
        CouchbaseRequestPeers.open(
            parent,
            new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210));
    assertThat(CouchbaseRequestPeers.consume(parent).getAddress()).isEqualTo("192.0.2.1");
    first.close();

    Scope retry =
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

  @Test
  void legacySemconvSkipsPeerCapture() throws UnknownHostException {
    assumeFalse(emitStableDatabaseSemconv());
    RequestSpan parent = mock(RequestSpan.class);

    assertThat(
            CouchbaseRequestPeers.open(
                parent,
                new InetSocketAddress(
                    InetAddress.getByAddress(new byte[] {(byte) 192, 0, 2, 1}), 11210)))
        .isNull();
  }
}
