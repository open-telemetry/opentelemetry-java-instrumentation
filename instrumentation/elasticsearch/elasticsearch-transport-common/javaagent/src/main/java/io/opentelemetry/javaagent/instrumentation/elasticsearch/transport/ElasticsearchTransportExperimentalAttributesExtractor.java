/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;
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

  private static final AttributeKey<String> ELASTICSEARCH_ACTION =
      stringKey("elasticsearch.action");
  private static final AttributeKey<String> ELASTICSEARCH_REQUEST =
      stringKey("elasticsearch.request");
  private static final AttributeKey<String> ELASTICSEARCH_REQUEST_INDICES =
      stringKey("elasticsearch.request.indices");
  private static final AttributeKey<String> ELASTICSEARCH_REQUEST_SEARCH_TYPES =
      stringKey("elasticsearch.request.search.types");
  private static final AttributeKey<String> ELASTICSEARCH_TYPE = stringKey("elasticsearch.type");
  private static final AttributeKey<String> ELASTICSEARCH_ID = stringKey("elasticsearch.id");
  private static final AttributeKey<Long> ELASTICSEARCH_VERSION = longKey("elasticsearch.version");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_BROADCAST_TOTAL =
      longKey("elasticsearch.shard.broadcast.total");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_BROADCAST_SUCCESSFUL =
      longKey("elasticsearch.shard.broadcast.successful");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_BROADCAST_FAILED =
      longKey("elasticsearch.shard.broadcast.failed");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_REPLICATION_TOTAL =
      longKey("elasticsearch.shard.replication.total");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_REPLICATION_SUCCESSFUL =
      longKey("elasticsearch.shard.replication.successful");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_REPLICATION_FAILED =
      longKey("elasticsearch.shard.replication.failed");
  private static final AttributeKey<Long> ELASTICSEARCH_RESPONSE_STATUS =
      longKey("elasticsearch.response.status");
  private static final AttributeKey<Long> ELASTICSEARCH_SHARD_BULK_ID =
      longKey("elasticsearch.shard.bulk.id");
  private static final AttributeKey<String> ELASTICSEARCH_SHARD_BULK_INDEX =
      stringKey("elasticsearch.shard.bulk.index");
  private static final AttributeKey<Long> ELASTICSEARCH_NODE_FAILURES =
      longKey("elasticsearch.node.failures");
  private static final AttributeKey<String> ELASTICSEARCH_NODE_CLUSTER_NAME =
      stringKey("elasticsearch.node.cluster.name");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ElasticTransportRequest transportRequest) {
    Object request = transportRequest.getRequest();
    attributes.put(ELASTICSEARCH_ACTION, transportRequest.getAction().getClass().getSimpleName());
    attributes.put(ELASTICSEARCH_REQUEST, request.getClass().getSimpleName());

    if (request instanceof IndicesRequest) {
      IndicesRequest req = (IndicesRequest) request;
      String[] indices = req.indices();
      if (indices != null && indices.length > 0) {
        attributes.put(ELASTICSEARCH_REQUEST_INDICES, String.join(",", indices));
      }
    }
    if (request instanceof SearchRequest) {
      SearchRequest req = (SearchRequest) request;
      String[] types = req.types();
      if (types != null && types.length > 0) {
        attributes.put(ELASTICSEARCH_REQUEST_SEARCH_TYPES, String.join(",", types));
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ElasticTransportRequest request,
      @Nullable ActionResponse response,
      @Nullable Throwable error) {
    if (response instanceof GetResponse) {
      GetResponse resp = (GetResponse) response;
      attributes.put(ELASTICSEARCH_TYPE, resp.getType());
      attributes.put(ELASTICSEARCH_ID, resp.getId());
      attributes.put(ELASTICSEARCH_VERSION, resp.getVersion());
    }

    if (response instanceof BroadcastResponse) {
      BroadcastResponse resp = (BroadcastResponse) response;
      attributes.put(ELASTICSEARCH_SHARD_BROADCAST_TOTAL, resp.getTotalShards());
      attributes.put(ELASTICSEARCH_SHARD_BROADCAST_SUCCESSFUL, resp.getSuccessfulShards());
      attributes.put(ELASTICSEARCH_SHARD_BROADCAST_FAILED, resp.getFailedShards());
    }

    if (response instanceof ReplicationResponse) {
      ReplicationResponse resp = (ReplicationResponse) response;
      attributes.put(ELASTICSEARCH_SHARD_REPLICATION_TOTAL, resp.getShardInfo().getTotal());
      attributes.put(
          ELASTICSEARCH_SHARD_REPLICATION_SUCCESSFUL, resp.getShardInfo().getSuccessful());
      attributes.put(ELASTICSEARCH_SHARD_REPLICATION_FAILED, resp.getShardInfo().getFailed());
    }

    if (response instanceof IndexResponse) {
      attributes.put(
          ELASTICSEARCH_RESPONSE_STATUS, ((IndexResponse) response).status().getStatus());
    }

    if (response instanceof BulkShardResponse) {
      BulkShardResponse resp = (BulkShardResponse) response;
      attributes.put(ELASTICSEARCH_SHARD_BULK_ID, resp.getShardId().getId());
      attributes.put(ELASTICSEARCH_SHARD_BULK_INDEX, resp.getShardId().getIndexName());
    }

    if (response instanceof BaseNodesResponse) {
      BaseNodesResponse<?> resp = (BaseNodesResponse<?>) response;
      if (resp.hasFailures()) {
        attributes.put(ELASTICSEARCH_NODE_FAILURES, resp.failures().size());
      }
      attributes.put(ELASTICSEARCH_NODE_CLUSTER_NAME, resp.getClusterName().value());
    }
  }
}
