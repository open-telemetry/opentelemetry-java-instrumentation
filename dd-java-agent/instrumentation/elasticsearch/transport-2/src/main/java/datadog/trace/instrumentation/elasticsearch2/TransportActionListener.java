package datadog.trace.instrumentation.elasticsearch2;

import static datadog.trace.instrumentation.elasticsearch.ElasticsearchTransportClientDecorator.DECORATE;

import com.google.common.base.Joiner;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
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
      if (req.indices() != null) {
        span.setTag("elasticsearch.request.indices", Joiner.on(",").join(req.indices()));
      }
    }
    if (request instanceof SearchRequest) {
      final SearchRequest req = (SearchRequest) request;
      span.setTag("elasticsearch.request.search.types", Joiner.on(",").join(req.types()));
    }
    if (request instanceof DocumentRequest) {
      final DocumentRequest req = (DocumentRequest) request;
      span.setTag("elasticsearch.request.write.type", req.type());
      span.setTag("elasticsearch.request.write.routing", req.routing());
    }
  }

  @Override
  public void onResponse(final T response) {
    if (response.remoteAddress() != null) {
      Tags.PEER_HOSTNAME.set(span, response.remoteAddress().getHost());
      Tags.PEER_HOST_IPV4.set(span, response.remoteAddress().getAddress());
      Tags.PEER_PORT.set(span, response.remoteAddress().getPort());
    }

    if (response instanceof GetResponse) {
      final GetResponse resp = (GetResponse) response;
      span.setTag("elasticsearch.type", resp.getType());
      span.setTag("elasticsearch.id", resp.getId());
      span.setTag("elasticsearch.version", resp.getVersion());
    }

    if (response instanceof BroadcastResponse) {
      final BroadcastResponse resp = (BroadcastResponse) response;
      span.setTag("elasticsearch.shard.broadcast.total", resp.getTotalShards());
      span.setTag("elasticsearch.shard.broadcast.successful", resp.getSuccessfulShards());
      span.setTag("elasticsearch.shard.broadcast.failed", resp.getFailedShards());
    }

    if (response instanceof BulkShardResponse) {
      final BulkShardResponse resp = (BulkShardResponse) response;
      span.setTag("elasticsearch.shard.bulk.id", resp.getShardId().getId());
      span.setTag("elasticsearch.shard.bulk.index", resp.getShardId().getIndex());
    }

    if (response instanceof BaseNodesResponse) {
      final BaseNodesResponse resp = (BaseNodesResponse) response;
      if (resp.failures().length > 0) {
        span.setTag("elasticsearch.node.failures", resp.failures().length);
      }
      span.setTag("elasticsearch.node.cluster.name", resp.getClusterName().value());
    }

    try {
      listener.onResponse(response);
    } finally {
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void onFailure(final Throwable e) {
    DECORATE.onError(span, e);

    try {
      listener.onFailure(e);
    } finally {
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }
}
