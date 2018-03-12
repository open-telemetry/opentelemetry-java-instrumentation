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
import org.apache.http.client.RedirectStrategy;
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
  private static final String COMPONENT_NAME = "apache-httpclient";
  /**
   * Id of {@link HttpClientContext#setAttribute(String, Object)} representing span associated with
   * the current client processing. Referenced span is local span not a span representing HTTP
   * communication.
   */
  private static final String ACTIVE_SPAN = DDTracingClientExec.class.getName() + ".activeSpan";
  /**
   * Tracing {@link ClientExecChain} is executed after redirect exec, so on redirects it is called
   * multiple times. This is used as an id for {@link HttpClientContext#setAttribute(String,
   * Object)} to store number of redirects.
   */
  private static final String REDIRECT_COUNT =
      DDTracingClientExec.class.getName() + ".redirectCount";

  private final RedirectStrategy redirectStrategy;
  private final ClientExecChain requestExecutor;
  private final boolean redirectHandlingDisabled;

  private final Tracer tracer;

  public DDTracingClientExec(
      final ClientExecChain clientExecChain,
      final RedirectStrategy redirectStrategy,
      final boolean redirectHandlingDisabled,
      final Tracer tracer) {
    this.requestExecutor = clientExecChain;
    this.redirectStrategy = redirectStrategy;
    this.redirectHandlingDisabled = redirectHandlingDisabled;
    this.tracer = tracer;
  }

  @Override
  public CloseableHttpResponse execute(
      final HttpRoute route,
      final HttpRequestWrapper request,
      final HttpClientContext clientContext,
      final HttpExecutionAware execAware)
      throws IOException, HttpException {

    Scope localScope = clientContext.getAttribute(ACTIVE_SPAN, Scope.class);
    CloseableHttpResponse response = null;
    try {
      if (localScope == null) {
        localScope = createLocalScope(request, clientContext);
      }

      return (response = createNetworkSpan(localScope, route, request, clientContext, execAware));
    } catch (final Exception e) {
      localScope.close();
      throw e;
    } finally {
      if (response != null) {
        /**
         * This exec runs after {@link org.apache.http.impl.execchain.RedirectExec} which loops
         * until there is no redirect or reaches max redirect count. {@link RedirectStrategy} is
         * used to decide whether localScope should be finished or not. If there is a redirect
         * localScope is not finished and redirect is logged.
         */
        Integer redirectCount = clientContext.getAttribute(REDIRECT_COUNT, Integer.class);
        if (!redirectHandlingDisabled
            && clientContext.getRequestConfig().isRedirectsEnabled()
            && redirectStrategy.isRedirected(request, response, clientContext)
            && ++redirectCount < clientContext.getRequestConfig().getMaxRedirects()) {
          clientContext.setAttribute(REDIRECT_COUNT, redirectCount);
        } else {
          localScope.close();
        }
      }
    }
  }

  private Scope createLocalScope(
      final HttpRequest httpRequest, final HttpClientContext clientContext) {
    final Tracer.SpanBuilder spanBuilder =
        tracer
            .buildSpan(httpRequest.getRequestLine().getMethod())
            .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME);

    final Scope scope = spanBuilder.startActive(true);
    clientContext.setAttribute(ACTIVE_SPAN, scope);
    clientContext.setAttribute(REDIRECT_COUNT, 0);
    return scope;
  }

  private CloseableHttpResponse createNetworkSpan(
      final Scope parentScope,
      final HttpRoute route,
      final HttpRequestWrapper request,
      final HttpClientContext clientContext,
      final HttpExecutionAware execAware)
      throws IOException, HttpException {
    Scope networkScope = null;
    Span networkSpan = null;
    try {
      // This handlers runs untrapped in the client code
      // so we must ensure any unexpected agent errors are caught.
      try {
        networkScope =
            tracer
                .buildSpan(request.getMethod())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
                .asChildOf(parentScope.span())
                .startActive(true);
        networkSpan = networkScope.span();

        final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
        // AWS calls are often signed, so we can't add headers without breaking the signature.
        if (!awsClientCall) {
          tracer.inject(
              networkSpan.context(),
              Format.Builtin.HTTP_HEADERS,
              new HttpHeadersInjectAdapter(request));
        }
        // request tags
        Tags.HTTP_METHOD.set(networkSpan, request.getRequestLine().getMethod());
        Tags.HTTP_URL.set(networkSpan, request.getRequestLine().getUri());
        final URI uri = request.getURI();
        // zuul users have encountered cases where getURI returns null
        if (null != uri) {
          Tags.PEER_PORT.set(networkSpan, uri.getPort() == -1 ? 80 : uri.getPort());
          Tags.PEER_HOSTNAME.set(networkSpan, uri.getHost());
        }
      } catch (Exception e) {
        log.debug("failed to create network span", e);
      }

      final CloseableHttpResponse response =
          requestExecutor.execute(route, request, clientContext, execAware);

      try {
        // response tags
        if (null != networkSpan) {
          Tags.HTTP_STATUS.set(networkSpan, response.getStatusLine().getStatusCode());
        }
      } catch (Exception e) {
        log.debug("failed to set network span status", e);
      }

      return response;
    } catch (IOException | HttpException | RuntimeException e) {
      // error tags
      Tags.ERROR.set(networkSpan, Boolean.TRUE);
      networkSpan.log(Collections.singletonMap(ERROR_OBJECT, e));

      throw e;
    } finally {
      if (null != networkScope) {
        networkScope.close();
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
