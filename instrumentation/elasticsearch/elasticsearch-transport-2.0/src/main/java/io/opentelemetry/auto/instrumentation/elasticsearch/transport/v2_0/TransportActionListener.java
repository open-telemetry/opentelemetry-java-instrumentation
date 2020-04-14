/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.elasticsearch.transport.v2_0;

import static io.opentelemetry.auto.instrumentation.elasticsearch.ElasticsearchTransportClientDecorator.DECORATE;

import com.google.common.base.Joiner;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.DocumentRequest;
import org.elasticsearch.action.IndicesRequest;
import org.elasticsearch.action.bulk.BulkShardResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.broadcast.BroadcastResponse;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;

public class TransportActionListener<T extends ActionResponse> implements ActionListener<T> {

  private final ActionListener<T> listener;
  private final Span span;

  public TransportActionListener(
      final ActionRequest actionRequest, final ActionListener<T> listener, final Span span) {
    this.listener = listener;
    this.span = span;
    onRequest(actionRequest);
  }

  private void onRequest(final ActionRequest request) {
    if (request instanceof IndicesRequest) {
      final IndicesRequest req = (IndicesRequest) request;
      final String[] indices = req.indices();
      if (indices != null && indices.length > 0) {
        span.setAttribute("elasticsearch.request.indices", Joiner.on(",").join(indices));
      }
    }
    if (request instanceof SearchRequest) {
      final SearchRequest req = (SearchRequest) request;
      final String[] types = req.types();
      if (types != null && types.length > 0) {
        span.setAttribute("elasticsearch.request.search.types", Joiner.on(",").join(types));
      }
    }
    if (request instanceof DocumentRequest) {
      final DocumentRequest req = (DocumentRequest) request;
      span.setAttribute("elasticsearch.request.write.type", req.type());
      span.setAttribute("elasticsearch.request.write.routing", req.routing());
    }
  }

  @Override
  public void onResponse(final T response) {
    if (response.remoteAddress() != null) {
      span.setAttribute(MoreTags.NET_PEER_NAME, response.remoteAddress().getHost());
      span.setAttribute(MoreTags.NET_PEER_IP, response.remoteAddress().getAddress());
      span.setAttribute(MoreTags.NET_PEER_PORT, response.remoteAddress().getPort());
    }

    if (response instanceof GetResponse) {
      final GetResponse resp = (GetResponse) response;
      span.setAttribute("elasticsearch.type", resp.getType());
      span.setAttribute("elasticsearch.id", resp.getId());
      span.setAttribute("elasticsearch.version", resp.getVersion());
    }

    if (response instanceof BroadcastResponse) {
      final BroadcastResponse resp = (BroadcastResponse) response;
      span.setAttribute("elasticsearch.shard.broadcast.total", resp.getTotalShards());
      span.setAttribute("elasticsearch.shard.broadcast.successful", resp.getSuccessfulShards());
      span.setAttribute("elasticsearch.shard.broadcast.failed", resp.getFailedShards());
    }

    if (response instanceof BulkShardResponse) {
      final BulkShardResponse resp = (BulkShardResponse) response;
      span.setAttribute("elasticsearch.shard.bulk.id", resp.getShardId().getId());
      span.setAttribute("elasticsearch.shard.bulk.index", resp.getShardId().getIndex());
    }

    if (response instanceof BaseNodesResponse) {
      final BaseNodesResponse resp = (BaseNodesResponse) response;
      if (resp.failures() != null && resp.failures().length > 0) {
        span.setAttribute("elasticsearch.node.failures", resp.failures().length);
      }
      span.setAttribute("elasticsearch.node.cluster.name", resp.getClusterName().value());
    }

    try {
      listener.onResponse(response);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onFailure(final Throwable e) {
    DECORATE.onError(span, e);

    try {
      listener.onFailure(e);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
