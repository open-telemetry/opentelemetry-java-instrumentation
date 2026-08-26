/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Response;
import org.junit.jupiter.api.Test;

class ElasticsearchDbAttributesGetterTest {

  private static final ElasticsearchRestRequest REQUEST =
      ElasticsearchRestRequest.create("GET", "/");

  private final ElasticsearchDbAttributesGetter getter = new ElasticsearchDbAttributesGetter(false);

  @Test
  void capturesResolvedResponseHost() throws UnknownHostException {
    Response response = mock(Response.class);
    when(response.getHost())
        .thenReturn(
            new HttpHost(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 9200, "http"));

    assertThat(getter.getNetworkPeerAddress(REQUEST, response)).isEqualTo("127.0.0.1");
    assertThat(getter.getNetworkPeerPort(REQUEST, response)).isEqualTo(9200);
    assertThat(extractAttributes(response))
        .isEqualTo(Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1", NETWORK_PEER_PORT, 9200L));
  }

  @Test
  void doesNotResolveHostnameOnlyResponseHost() {
    Response response = mock(Response.class);
    when(response.getHost()).thenReturn(new HttpHost("elasticsearch.example", 9200, "http"));

    assertThat(getter.getNetworkPeerAddress(REQUEST, response)).isNull();
    assertThat(getter.getNetworkPeerPort(REQUEST, response)).isEqualTo(9200);
    assertThat(extractAttributes(response)).isEqualTo(Attributes.empty());
  }

  @Test
  void handlesMissingResponseHost() {
    Response response = mock(Response.class);

    assertThat(getter.getNetworkPeerAddress(REQUEST, null)).isNull();
    assertThat(getter.getNetworkPeerPort(REQUEST, null)).isNull();
    assertThat(getter.getNetworkPeerAddress(REQUEST, response)).isNull();
    assertThat(getter.getNetworkPeerPort(REQUEST, response)).isNull();
  }

  private Attributes extractAttributes(Response response) {
    AttributesBuilder attributes = Attributes.builder();
    DbClientAttributesExtractor.create(getter)
        .onEnd(attributes, Context.root(), REQUEST, response, null);
    return attributes.build();
  }
}
