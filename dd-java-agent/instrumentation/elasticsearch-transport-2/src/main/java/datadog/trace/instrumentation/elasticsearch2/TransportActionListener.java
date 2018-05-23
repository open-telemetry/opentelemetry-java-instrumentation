package datadog.trace.instrumentation.elasticsearch2;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionResponse;

public class TransportActionListener<T> implements ActionListener<T> {

  private final ActionListener<T> listener;
  private final Span span;

  public TransportActionListener(final ActionListener<T> listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onResponse(final T response) {
    if (response instanceof ActionResponse) {
      final ActionResponse ar = (ActionResponse) response;
      if (ar.remoteAddress() != null) {
        Tags.PEER_HOSTNAME.set(span, ar.remoteAddress().getHost());
        Tags.PEER_PORT.set(span, ar.remoteAddress().getPort());
      }
    }

    try {
      listener.onResponse(response);
    } finally {
      span.finish();
    }
  }

  @Override
  public void onFailure(final Throwable e) {
    span.log(Collections.singletonMap(ERROR_OBJECT, e));

    try {
      listener.onFailure(e);
    } finally {
      span.finish();
    }
  }
}
