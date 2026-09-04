/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.network.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class NetworkPeerCaptureTest {

  @Test
  void doesNotCaptureWhenInactive() {
    NetworkPeerCapture capture = new NetworkPeerCapture();
    Context context = Context.root();

    NetworkPeerCapture.capture(
        context, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200));

    assertThat(NetworkPeerCapture.isActive(context)).isFalse();
    assertThat(capture.getPeerAddress()).isNull();
  }

  @Test
  void capturesOnlyResolvedInetSocketAddress() {
    NetworkPeerCapture capture = new NetworkPeerCapture();
    Context context = capture.storeInContext(Context.root());

    assertThat(NetworkPeerCapture.isActive(context)).isTrue();
    NetworkPeerCapture.capture(context, null);
    NetworkPeerCapture.capture(context, new SocketAddress() {});
    NetworkPeerCapture.capture(
        context, InetSocketAddress.createUnresolved("network.example", 9200));
    assertThat(capture.getPeerAddress()).isNull();

    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);
    NetworkPeerCapture.capture(context, peer);

    assertThat(capture.getPeerAddress()).isEqualTo(peer);
  }

  @Test
  void updatesNestedCaptures() {
    NetworkPeerCapture outer = new NetworkPeerCapture();
    Context outerContext = outer.storeInContext(Context.root());
    NetworkPeerCapture inner = new NetworkPeerCapture();
    Context innerContext = inner.storeInContext(outerContext);
    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);

    NetworkPeerCapture.capture(innerContext, peer);

    assertThat(outer.getPeerAddress()).isEqualTo(peer);
    assertThat(inner.getPeerAddress()).isEqualTo(peer);
  }

  @Test
  void lastCaptureWins() {
    NetworkPeerCapture capture = new NetworkPeerCapture();
    Context context = capture.storeInContext(Context.root());
    InetSocketAddress first = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200);
    InetSocketAddress last = new InetSocketAddress(InetAddress.getLoopbackAddress(), 9201);

    NetworkPeerCapture.capture(context, first);
    NetworkPeerCapture.capture(context, last);

    assertThat(capture.getPeerAddress()).isEqualTo(last);
  }
}
