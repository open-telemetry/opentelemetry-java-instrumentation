/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.bulk.BulkShardResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.broadcast.BroadcastResponse;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;

public class ElasticsearchTransportExperimentalAttributesExtractor
    implements AttributesExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  public void onStart(AttributesBuilder attributes, ElasticTransportRequest transportRequest) {
    Object request = transportRequest.getRequest();
    attributes.put("elasticsearch.action", transportRequest.getAction().getClass().getSimpleName());
    attributes.put("elasticsearch.request", request.getClass().getSimpleName());

    if (request instanceof IndicesRequest) {
      IndicesRequest req = (IndicesRequest) request;
      String[] indices = req.indices();
      if (indices != null && indices.length > 0) {
        attributes.put("elasticsearch.request.indices", String.join(",", indices));
      }
    }
    if (request instanceof SearchRequest) {
      SearchRequest req = (SearchRequest) request;
      String[] types = req.types();
      if (types != null && types.length > 0) {
        attributes.put("elasticsearch.request.search.types", String.join(",", types));
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      ElasticTransportRequest request,
      ActionResponse response,
      @Nullable Throwable error) {
    if (response instanceof GetResponse) {
      GetResponse resp = (GetResponse) response;
      attributes.put("elasticsearch.type", resp.getType());
      attributes.put("elasticsearch.id", resp.getId());
      attributes.put("elasticsearch.version", resp.getVersion());
    }

    if (response instanceof BroadcastResponse) {
      BroadcastResponse resp = (BroadcastResponse) response;
      attributes.put("elasticsearch.shard.broadcast.total", resp.getTotalShards());
      attributes.put("elasticsearch.shard.broadcast.successful", resp.getSuccessfulShards());
      attributes.put("elasticsearch.shard.broadcast.failed", resp.getFailedShards());
    }

    if (response instanceof ReplicationResponse) {
      ReplicationResponse resp = (ReplicationResponse) response;
      attributes.put("elasticsearch.shard.replication.total", resp.getShardInfo().getTotal());
      attributes.put(
          "elasticsearch.shard.replication.successful", resp.getShardInfo().getSuccessful());
      attributes.put("elasticsearch.shard.replication.failed", resp.getShardInfo().getFailed());
    }

    if (response instanceof IndexResponse) {
      attributes.put(
          "elasticsearch.response.status", ((IndexResponse) response).status().getStatus());
    }

    if (response instanceof BulkShardResponse) {
      BulkShardResponse resp = (BulkShardResponse) response;
      attributes.put("elasticsearch.shard.bulk.id", resp.getShardId().getId());
      attributes.put("elasticsearch.shard.bulk.index", resp.getShardId().getIndexName());
    }

    if (response instanceof BaseNodesResponse) {
      BaseNodesResponse<?> resp = (BaseNodesResponse<?>) response;
      if (resp.hasFailures()) {
        attributes.put("elasticsearch.node.failures", resp.failures().size());
      }
      attributes.put("elasticsearch.node.cluster.name", resp.getClusterName().value());
    }
  }
}
