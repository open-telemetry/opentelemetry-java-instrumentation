/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseRequestInfoTest {
  @Test
  void subscriptionsCanReadNetworkCaptureFromEagerAssembly() {
    CouchbaseRequestInfo assembly = CouchbaseRequestInfo.create("test", null, "query");
    CouchbaseRequestInfo first = assembly.copySupplier().get();
    CouchbaseRequestInfo second = assembly.copySupplier().get();
    InetSocketAddress eagerPeer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210);

    assembly.setNode(eagerPeer);

    assertThat(first.getNode().getPeerAddress()).isEqualTo(eagerPeer);
    assertThat(second.getNode().getPeerAddress()).isEqualTo(eagerPeer);

    InetSocketAddress subscriptionPeer =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 11211);
    first.setNode(subscriptionPeer);

    assertThat(first.getNode().getPeerAddress()).isEqualTo(subscriptionPeer);
    assertThat(second.getNode().getPeerAddress()).isEqualTo(eagerPeer);
  }

  @Test
  void subscriptionsKeepIndependentLazyNetworkCapture() {
    CouchbaseRequestInfo assembly = CouchbaseRequestInfo.create("test", null, "query");
    CouchbaseRequestInfo first = assembly.copySupplier().get();
    CouchbaseRequestInfo second = assembly.copySupplier().get();
    InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), 11210);

    first.setNode(peer);

    assertThat(first.getNode().getPeerAddress()).isEqualTo(peer);
    assertThat(second.getNode()).isNull();
    assertThat(assembly.getNode()).isNull();
  }
}
