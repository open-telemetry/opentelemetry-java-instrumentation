/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseRequestInfo;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class CouchbaseAttributesGetterTest {

  @Test
  void usesCanonicalServerAddressAndActualPeerAddress() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", getClass(), "operation");
    InetSocketAddress peerAddress = new InetSocketAddress("192.0.2.1", 32768);
    request.setEndpoint(peerAddress, "node.example:11210");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(peerAddress);
    assertThat(extractServerAttributes(request))
        .isEqualTo(Attributes.of(SERVER_ADDRESS, "node.example", SERVER_PORT, 11210L));
  }

  @Test
  void retainsLastContactedEndpointAsAPair() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", getClass(), "operation");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);

    request.setEndpoint(firstPeer, "2001:db8::1:11210");
    assertThat(extractServerAttributes(request))
        .isEqualTo(Attributes.of(SERVER_ADDRESS, "2001:db8::1", SERVER_PORT, 11210L));

    request.setEndpoint(secondPeer, "[2001:db8::2]:11211");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(extractServerAttributes(request))
        .isEqualTo(Attributes.of(SERVER_ADDRESS, "2001:db8::2", SERVER_PORT, 11211L));
  }

  @Test
  void copyResetsPerSubscriptionState() {
    CouchbaseRequestInfo request = CouchbaseRequestInfo.create("bucket", getClass(), "operation");
    InetSocketAddress firstPeer = new InetSocketAddress("192.0.2.1", 32768);
    InetSocketAddress secondPeer = new InetSocketAddress("192.0.2.2", 32769);
    request.setEndpoint(secondPeer, "second.example:11211");

    CouchbaseRequestInfo copy = request.copySupplier().get();
    copy.setEndpoint(firstPeer, "first.example:11210");

    CouchbaseAttributesGetter getter = new CouchbaseAttributesGetter();
    assertThat(getter.getNetworkPeerInetSocketAddress(request, null)).isEqualTo(secondPeer);
    assertThat(extractServerAttributes(request))
        .isEqualTo(Attributes.of(SERVER_ADDRESS, "second.example", SERVER_PORT, 11211L));
    assertThat(getter.getNetworkPeerInetSocketAddress(copy, null)).isEqualTo(firstPeer);
    assertThat(extractServerAttributes(copy))
        .isEqualTo(Attributes.of(SERVER_ADDRESS, "first.example", SERVER_PORT, 11210L));
  }

  private static Attributes extractServerAttributes(CouchbaseRequestInfo request) {
    AttributesBuilder attributes = Attributes.builder();
    new CouchbaseServerAttributesExtractor().onEnd(attributes, Context.root(), request, null, null);
    return attributes.build();
  }
}
