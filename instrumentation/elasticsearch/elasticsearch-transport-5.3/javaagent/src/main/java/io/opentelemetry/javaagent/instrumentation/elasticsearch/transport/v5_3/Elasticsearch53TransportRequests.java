/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTargets;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.TransportAddress;

class Elasticsearch53TransportRequests {

  static ElasticTransportRequest request(
      AbstractClient client, Object action, Object actionRequest) {
    if (!ElasticsearchTransportServerTargets.isCaptured(client)) {
      ElasticsearchTransportServerTargets.capture(client, configuredEndpoints(client));
    }
    return ElasticTransportRequest.create(
        action,
        actionRequest,
        ElasticsearchTransportServerTargets.address(client),
        ElasticsearchTransportServerTargets.port(client));
  }

  @Nullable
  private static List<ElasticsearchTransportServerTarget.Endpoint> configuredEndpoints(
      AbstractClient client) {
    if (!(client instanceof TransportClient)) {
      return null;
    }
    List<ElasticsearchTransportServerTarget.Endpoint> endpoints = new ArrayList<>();
    for (TransportAddress address : ((TransportClient) client).transportAddresses()) {
      endpoints.add(
          new ElasticsearchTransportServerTarget.Endpoint(address.getAddress(), address.getPort()));
    }
    return endpoints;
  }

  private Elasticsearch53TransportRequests() {}
}
