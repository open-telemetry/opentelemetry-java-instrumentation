package datadog.trace.instrumentation.apachehttpclient;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;

/**
 * Tracing is added before {@link org.apache.http.impl.execchain.ProtocolExec} which is invoked as
 * the next to last. Note that {@link org.apache.http.impl.execchain.RedirectExec} is invoked before
 * so this exec has to deal with redirects.
 */
@Slf4j
public class DDTracingClientExec implements ClientExecChain {
  static final String COMPONENT_NAME = "apache-httpclient";
  static final String OPERATION_NAME = "http.request";

  private final ClientExecChain requestExecutor;

  private final Tracer tracer;

  public DDTracingClientExec(final ClientExecChain clientExecChain, final Tracer tracer) {
    requestExecutor = clientExecChain;
    this.tracer = tracer;
  }

  @Override
  public CloseableHttpResponse execute(
      final HttpRoute route,
      final HttpRequestWrapper request,
      final HttpClientContext clientContext,
      final HttpExecutionAware execAware)
      throws IOException, HttpException {
    Scope scope = null;
    Span span = null;
    try {
      // This handlers runs untrapped in the client code
      // so we must ensure any unexpected agent errors are caught.
      try {
        scope =
            tracer
                .buildSpan(OPERATION_NAME)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
                .startActive(true);
        span = scope.span();

        final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
        // AWS calls are often signed, so we can't add headers without breaking the signature.
        if (!awsClientCall) {
          tracer.inject(
              span.context(), Format.Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));
        }
        // request tags
        Tags.HTTP_METHOD.set(span, request.getRequestLine().getMethod());
        Tags.HTTP_URL.set(span, request.getRequestLine().getUri());
        final URI uri = request.getURI();
        // zuul users have encountered cases where getURI returns null
        if (null != uri) {
          Tags.PEER_PORT.set(span, uri.getPort() == -1 ? 80 : uri.getPort());
          Tags.PEER_HOSTNAME.set(span, uri.getHost());
        }
      } catch (final Exception e) {
        log.debug("failed to create span", e);
      }

      final CloseableHttpResponse response =
          requestExecutor.execute(route, request, clientContext, execAware);

      try {
        // response tags
        if (null != span) {
          Tags.HTTP_STATUS.set(span, response.getStatusLine().getStatusCode());
        }
      } catch (final Exception e) {
        log.debug("failed to set span status", e);
      }

      return response;
    } catch (final IOException | HttpException | RuntimeException e) {
      // error tags
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, e));

      throw e;
    } finally {
      if (null != scope) {
        scope.close();
      }
    }
  }

  public static class HttpHeadersInjectAdapter implements TextMap {

    private final HttpRequest httpRequest;

    public HttpHeadersInjectAdapter(final HttpRequest httpRequest) {
      this.httpRequest = httpRequest;
    }

    @Override
    public void put(final String key, final String value) {
      httpRequest.addHeader(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }
  }
}
