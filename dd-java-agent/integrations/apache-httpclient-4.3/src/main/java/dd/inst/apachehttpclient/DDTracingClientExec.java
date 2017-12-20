package dd.inst.apachehttpclient;

import io.opentracing.ActiveSpan;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
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
      ClientExecChain clientExecChain,
      RedirectStrategy redirectStrategy,
      boolean redirectHandlingDisabled,
      Tracer tracer) {
    this.requestExecutor = clientExecChain;
    this.redirectStrategy = redirectStrategy;
    this.redirectHandlingDisabled = redirectHandlingDisabled;
    this.tracer = tracer;
  }

  @Override
  public CloseableHttpResponse execute(
      HttpRoute route,
      HttpRequestWrapper request,
      HttpClientContext clientContext,
      HttpExecutionAware execAware)
      throws IOException, HttpException {

    ActiveSpan localSpan = clientContext.getAttribute(ACTIVE_SPAN, ActiveSpan.class);
    CloseableHttpResponse response = null;
    try {
      if (localSpan == null) {
        localSpan = createLocalSpan(request, clientContext);
      }

      return (response = createNetworkSpan(localSpan, route, request, clientContext, execAware));
    } catch (Exception e) {
      localSpan.deactivate();
      throw e;
    } finally {
      if (response != null) {
        /**
         * This exec runs after {@link org.apache.http.impl.execchain.RedirectExec} which loops
         * until there is no redirect or reaches max redirect count. {@link RedirectStrategy} is
         * used to decide whether localSpan should be finished or not. If there is a redirect
         * localSpan is not finished and redirect is logged.
         */
        Integer redirectCount = clientContext.getAttribute(REDIRECT_COUNT, Integer.class);
        if (!redirectHandlingDisabled
            && clientContext.getRequestConfig().isRedirectsEnabled()
            && redirectStrategy.isRedirected(request, response, clientContext)
            && ++redirectCount < clientContext.getRequestConfig().getMaxRedirects()) {
          clientContext.setAttribute(REDIRECT_COUNT, redirectCount);
        } else {
          localSpan.deactivate();
        }
      }
    }
  }

  private ActiveSpan createLocalSpan(HttpRequest httpRequest, HttpClientContext clientContext) {
    Tracer.SpanBuilder spanBuilder =
        tracer
            .buildSpan(httpRequest.getRequestLine().getMethod())
            .withTag(Tags.COMPONENT.getKey(), COMPONENT_NAME);

    ActiveSpan localSpan = spanBuilder.startActive();
    clientContext.setAttribute(ACTIVE_SPAN, localSpan);
    clientContext.setAttribute(REDIRECT_COUNT, 0);
    return localSpan;
  }

  private CloseableHttpResponse createNetworkSpan(
      ActiveSpan parentSpan,
      HttpRoute route,
      HttpRequestWrapper request,
      HttpClientContext clientContext,
      HttpExecutionAware execAware)
      throws IOException, HttpException {
    ActiveSpan networkSpan =
        tracer
            .buildSpan(request.getMethod())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .asChildOf(parentSpan)
            .startActive();
    tracer.inject(
        networkSpan.context(), Format.Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));

    try {
      // request tags
      Tags.HTTP_METHOD.set(networkSpan, request.getRequestLine().getMethod());
      final URI uri = request.getURI();
      Tags.HTTP_URL.set(networkSpan, request.getRequestLine().getUri());
      Tags.PEER_PORT.set(networkSpan, uri.getPort() == -1 ? 80 : uri.getPort());
      Tags.PEER_HOSTNAME.set(networkSpan, uri.getHost());

      final CloseableHttpResponse response =
          requestExecutor.execute(route, request, clientContext, execAware);

      // response tags
      Tags.HTTP_STATUS.set(networkSpan, response.getStatusLine().getStatusCode());

      return response;
    } catch (IOException | HttpException | RuntimeException e) {
      // error tags
      Tags.ERROR.set(networkSpan, Boolean.TRUE);
      networkSpan.log(Collections.singletonMap("error.object", e));

      throw e;
    } finally {
      networkSpan.deactivate();
    }
  }

  public static class HttpHeadersInjectAdapter implements TextMap {

    private HttpRequest httpRequest;

    public HttpHeadersInjectAdapter(HttpRequest httpRequest) {
      this.httpRequest = httpRequest;
    }

    @Override
    public void put(String key, String value) {
      httpRequest.addHeader(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }
  }
}
