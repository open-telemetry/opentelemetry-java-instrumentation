/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SearchPeerState;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class ElasticsearchDbAttributesGetterTest {

  private final ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(false);

  @Test
  void capturesPeerFromRequestState() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");
    Context context = request.getPeerState().storeInContext(Context.root());
    SearchPeerState.capture(context, new InetSocketAddress(InetAddress.getLoopbackAddress(), 9200));

    assertThat(getter.getNetworkPeerAddress(request, null)).isEqualTo("127.0.0.1");
    assertThat(getter.getNetworkPeerPort(request, null)).isEqualTo(9200);
    assertThat(extractAttributes(request))
        .isEqualTo(Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1", NETWORK_PEER_PORT, 9200L));
  }

  @Test
  void doesNotResolveConfiguredHostname() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");
    Context context = request.getPeerState().storeInContext(Context.root());
    SearchPeerState.capture(context, InetSocketAddress.createUnresolved("search.example", 9200));

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
    assertThat(extractAttributes(request)).isEqualTo(Attributes.empty());
  }

  @Test
  void handlesMissingPeer() {
    ElasticsearchRestRequest request = ElasticsearchRestRequest.create("GET", "/");

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
    assertThat(extractAttributes(request)).isEqualTo(Attributes.empty());
  }

  private Attributes extractAttributes(ElasticsearchRestRequest request) {
    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(getter)
        .onEnd(attributes, Context.root(), request, null, null);
    return attributes.build();
  }
}
