/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class SearchPeerStateTest {

  @Test
  void ignoresCaptureWithoutActiveState() {
    SearchPeerState state = new SearchPeerState();

    SearchPeerState.capture(
        Context.root(), new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200));

    assertThat(state.getPeerAddress()).isNull();
  }

  @Test
  void capturesOnlyResolvedInetSocketAddress() {
    SearchPeerState state = new SearchPeerState();
    Context context = state.storeInContext(Context.root());

    SearchPeerState.capture(context, null);
    SearchPeerState.capture(context, new SocketAddress() {});
    SearchPeerState.capture(context, InetSocketAddress.createUnresolved("search.example", 9200));
    assertThat(state.getPeerAddress()).isNull();

    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);
    SearchPeerState.capture(context, peer);

    assertThat(state.getPeerAddress()).isEqualTo(peer);
  }

  @Test
  void updatesNestedStates() {
    SearchPeerState outer = new SearchPeerState();
    Context outerContext = outer.storeInContext(Context.root());
    SearchPeerState inner = new SearchPeerState();
    Context innerContext = inner.storeInContext(outerContext);
    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);

    SearchPeerState.capture(innerContext, peer);

    assertThat(outer.getPeerAddress()).isEqualTo(peer);
    assertThat(inner.getPeerAddress()).isEqualTo(peer);
  }

  @Test
  void laterAttemptsReplaceEarlierPeer() {
    SearchPeerState state = new SearchPeerState();
    Context context = state.storeInContext(Context.root());
    InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);
    InetSocketAddress last = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9201);

    SearchPeerState.capture(context, first);
    SearchPeerState.capture(context, last);

    assertThat(state.getPeerAddress()).isEqualTo(last);
  }
}
