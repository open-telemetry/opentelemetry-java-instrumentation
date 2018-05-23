package datadog.trace.instrumentation.elasticsearch5;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;

public class TransportActionListener<T extends ActionResponse> implements ActionListener<T> {

  private final ActionListener<T> listener;
  private final Span span;

  public TransportActionListener(final ActionListener<T> listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onResponse(final T response) {
    if (response.remoteAddress() != null) {
      Tags.PEER_HOSTNAME.set(span, response.remoteAddress().getHost());
      Tags.PEER_HOST_IPV4.set(span, response.remoteAddress().getAddress());
      Tags.PEER_PORT.set(span, response.remoteAddress().getPort());
    }

    try {
      listener.onResponse(response);
    } finally {
      span.finish();
    }
  }

  @Override
  public void onFailure(final Exception e) {
    span.log(Collections.singletonMap(ERROR_OBJECT, e));

    try {
      listener.onFailure(e);
    } finally {
      span.finish();
    }
  }
}
