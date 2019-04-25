package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class HttpClientDecorator<REQUEST, RESPONSE> extends ClientDecorator {

  protected abstract String method(REQUEST request);

  protected abstract String url(REQUEST request);

  protected abstract String hostname(REQUEST request);

  protected abstract Integer port(REQUEST request);

  protected abstract Integer status(RESPONSE response);

  @Override
  protected String spanType() {
    return DDSpanTypes.HTTP_CLIENT;
  }

  @Override
  protected String service() {
    return null;
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      Tags.HTTP_METHOD.set(span, method(request));
      Tags.HTTP_URL.set(span, url(request));
      Tags.PEER_HOSTNAME.set(span, hostname(request));
      final Integer port = port(request);
      Tags.PEER_PORT.set(span, port != null && port > 0 ? port : null);

      if (Config.get().isHttpClientSplitByDomain()) {
        span.setTag(DDTags.SERVICE_NAME, hostname(request));
      }
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        Tags.HTTP_STATUS.set(span, status);

        if (Config.get().getHttpClientErrorStatuses().contains(status)) {
          Tags.ERROR.set(span, true);
        }
      }
    }
    return span;
  }
}
