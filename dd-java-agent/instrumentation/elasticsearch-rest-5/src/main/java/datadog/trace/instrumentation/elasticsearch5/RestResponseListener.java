package datadog.trace.instrumentation.elasticsearch5;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(final ResponseListener listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(final Response response) {
    if (response.getHost() != null) {
      Tags.PEER_HOSTNAME.set(span, response.getHost().getHostName());
      Tags.PEER_PORT.set(span, response.getHost().getPort());
    }

    try {
      listener.onSuccess(response);
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
