/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTargets;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;

public class Elasticsearch5TransportRequests {

  static ElasticTransportRequest request(
      AbstractClient client, Object action, Object actionRequest) {
    return ElasticTransportRequest.create(
        action, actionRequest, ElasticsearchTransportServerTargets.get(client));
  }

  public static void updateServerTarget(TransportClient client) {
    Object updateLock = ElasticsearchTransportServerTargets.getUpdateLock(client);
    if (updateLock == null) {
      return;
    }
    synchronized (updateLock) {
      List<ElasticsearchTransportServerTarget.Endpoint> endpoints = new ArrayList<>();
      for (TransportAddress address : client.transportAddresses()) {
        endpoints.add(
            new ElasticsearchTransportServerTarget.Endpoint(host(address), address.getPort()));
      }
      ElasticsearchTransportServerTargets.update(client, endpoints);
    }
  }

  private static String host(TransportAddress address) {
    // TransportAddress.getHost() formats the resolved IP address before Elasticsearch 5.1, so read
    // the configured host string from the wrapped socket address instead
    return address instanceof InetSocketTransportAddress
        ? ((InetSocketTransportAddress) address).address().getHostString()
        : address.getHost();
  }

  private Elasticsearch5TransportRequests() {}
}
