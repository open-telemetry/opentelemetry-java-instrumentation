package com.datadoghq.agent.integration;

import fi.iki.elonen.NanoHTTPD;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A simple http server used for testing.<br>
 * Binds locally to {@link #PORT}.
 *
 * <p>To start: {@link #startServer()}<br>
 * to stop: {@link #stopServer()}
 */
public class TestHttpServer extends NanoHTTPD {
  /** Port the test server will bind. */
  public static final int PORT = 8089;
  /**
   * By default the test server will mock a datadog traced server. Set this header to a value of
   * false to disable.
   */
  public static final String IS_DD_SERVER = "is-dd-server";

  private static TestHttpServer INSTANCE = null;

  /**
   * Start the server. Has no effect if already started.
   *
   * @throws IOException
   */
  public static synchronized void startServer() throws IOException {
    if (null == INSTANCE) {
      INSTANCE = new TestHttpServer();
    }
  }

  /** Stop the server. Has no effect if already stopped. */
  public static synchronized void stopServer() {
    if (null != INSTANCE) {
      INSTANCE.stop();
      INSTANCE = null;
    }
  }

  private TestHttpServer() throws IOException {
    super(PORT);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String msg = "<html><body><h1>Hello test.</h1>\n";
    Map<String, List<String>> params = session.getParameters();
    boolean isDDServer = true;
    if (params.containsKey(IS_DD_SERVER)) {
      isDDServer = Boolean.parseBoolean(params.get(IS_DD_SERVER).get(0));
    }
    if (isDDServer) {
      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new HttpSessionInjectAdapter(session));
      ActiveSpan span =
          GlobalTracer.get()
              .buildSpan("test-http-server")
              .asChildOf(extractedContext)
              .startActive();
      span.deactivate();
    }
    return newFixedLengthResponse(
        NanoHTTPD.Response.Status.OK, "text/html", msg + "</body></html>\n");
  }

  private class HttpSessionInjectAdapter implements TextMap {

    private IHTTPSession session;

    public HttpSessionInjectAdapter(IHTTPSession httpRequest) {
      this.session = httpRequest;
    }

    @Override
    public void put(String key, String value) {
      session.getHeaders().put(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return session.getHeaders().entrySet().iterator();
    }
  }
}
