/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_0;

import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportClientTracer.TRACER;

import com.google.common.base.Joiner;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.DocumentRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.bulk.BulkShardResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.broadcast.BroadcastResponse;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.action.support.replication.ReplicationResponse;

public class TransportActionListener<T extends ActionResponse> implements ActionListener<T> {

  private final ActionListener<T> listener;
  private final Span span;

  public TransportActionListener(
      ActionRequest actionRequest, ActionListener<T> listener, Span span) {
    this.listener = listener;
    this.span = span;
    onRequest(actionRequest);
  }

  private void onRequest(ActionRequest request) {
    if (request instanceof IndicesRequest) {
      IndicesRequest req = (IndicesRequest) request;
      String[] indices = req.indices();
      if (indices != null && indices.length > 0) {
        span.setAttribute("elasticsearch.request.indices", Joiner.on(",").join(indices));
      }
    }
    if (request instanceof SearchRequest) {
      SearchRequest req = (SearchRequest) request;
      String[] types = req.types();
      if (types != null && types.length > 0) {
        span.setAttribute("elasticsearch.request.search.types", Joiner.on(",").join(types));
      }
    }
    if (request instanceof DocumentRequest) {
      DocumentRequest req = (DocumentRequest) request;
      span.setAttribute("elasticsearch.request.write.type", req.type());
      span.setAttribute("elasticsearch.request.write.routing", req.routing());
    }
  }

  @Override
  public void onResponse(T response) {
    if (response.remoteAddress() != null) {
      NetPeerUtils.setNetPeer(
          span, response.remoteAddress().getHost(), response.remoteAddress().getAddress());
      span.setAttribute(
          SemanticAttributes.NET_PEER_PORT, (long) response.remoteAddress().getPort());
    }

    if (response instanceof GetResponse) {
      GetResponse resp = (GetResponse) response;
      span.setAttribute("elasticsearch.type", resp.getType());
      span.setAttribute("elasticsearch.id", resp.getId());
      span.setAttribute("elasticsearch.version", resp.getVersion());
    }

    if (response instanceof BroadcastResponse) {
      BroadcastResponse resp = (BroadcastResponse) response;
      span.setAttribute("elasticsearch.shard.broadcast.total", resp.getTotalShards());
      span.setAttribute("elasticsearch.shard.broadcast.successful", resp.getSuccessfulShards());
      span.setAttribute("elasticsearch.shard.broadcast.failed", resp.getFailedShards());
    }

    if (response instanceof ReplicationResponse) {
      ReplicationResponse resp = (ReplicationResponse) response;
      span.setAttribute("elasticsearch.shard.replication.total", resp.getShardInfo().getTotal());
      span.setAttribute(
          "elasticsearch.shard.replication.successful", resp.getShardInfo().getSuccessful());
      span.setAttribute("elasticsearch.shard.replication.failed", resp.getShardInfo().getFailed());
    }

    if (response instanceof IndexResponse) {
      span.setAttribute(
          "elasticsearch.response.status", ((IndexResponse) response).status().getStatus());
    }

    if (response instanceof BulkShardResponse) {
      BulkShardResponse resp = (BulkShardResponse) response;
      span.setAttribute("elasticsearch.shard.bulk.id", resp.getShardId().getId());
      span.setAttribute("elasticsearch.shard.bulk.index", resp.getShardId().getIndexName());
    }

    if (response instanceof BaseNodesResponse) {
      BaseNodesResponse resp = (BaseNodesResponse) response;
      if (resp.hasFailures()) {
        span.setAttribute("elasticsearch.node.failures", resp.failures().size());
      }
      span.setAttribute("elasticsearch.node.cluster.name", resp.getClusterName().value());
    }

    TRACER.end(span);
    listener.onResponse(response);
  }

  @Override
  public void onFailure(Exception e) {
    TRACER.endExceptionally(span, e);
    listener.onFailure(e);
  }
}
