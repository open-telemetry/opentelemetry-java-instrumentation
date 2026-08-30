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
import org.elasticsearch.common.transport.TransportAddress;

public class Elasticsearch5TransportRequests {

  static ElasticTransportRequest request(
      AbstractClient client, Object action, Object actionRequest) {
    ElasticsearchTransportServerTarget target = ElasticsearchTransportServerTargets.get(client);
    return ElasticTransportRequest.create(
        action,
        actionRequest,
        target == null ? null : target.getAddress(),
        target == null ? null : target.getPort());
  }

  public static void updateServerTarget(TransportClient client) {
    List<ElasticsearchTransportServerTarget.Endpoint> endpoints = new ArrayList<>();
    for (TransportAddress address : client.transportAddresses()) {
      endpoints.add(
          new ElasticsearchTransportServerTarget.Endpoint(address.getHost(), address.getPort()));
    }
    ElasticsearchTransportServerTargets.update(client, endpoints);
  }

  private Elasticsearch5TransportRequests() {}
}
