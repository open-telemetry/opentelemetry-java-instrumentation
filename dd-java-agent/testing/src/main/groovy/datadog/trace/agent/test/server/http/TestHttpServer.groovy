package datadog.trace.agent.test.server.http

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import io.opentracing.SpanContext
import io.opentracing.Tracer
import io.opentracing.propagation.Format
import io.opentracing.util.GlobalTracer
import org.eclipse.jetty.http.HttpMethods
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.handler.HandlerList

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.atomic.AtomicReference

class TestHttpServer implements AutoCloseable {

  static TestHttpServer httpServer(boolean start = true,
                                   @DelegatesTo(value = TestHttpServer, strategy = Closure.DELEGATE_FIRST) Closure spec) {

    def server = new TestHttpServer()
    def clone = (Closure) spec.clone()
    clone.delegate = server
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(server)
    if (start) {
      server.start()
    }
    return server
  }

  private final Server internalServer
  private HandlersSpec handlers

  public Tracer tracer = GlobalTracer.get()


  private URI address
  private final AtomicReference<HandlerApi.RequestApi> last = new AtomicReference<>()

  private TestHttpServer() {
    internalServer = new Server(0)
  }

  def start() {
    if (internalServer.isStarted()) {
      return
    }

    assert handlers != null: "handlers must be defined"
    def handlerList = new HandlerList()
    handlerList.handlers = handlers.configured
    internalServer.handler = handlerList
    internalServer.start()
    // set after starting, otherwise two callbacks get added.
    internalServer.stopAtShutdown = true

    address = new URI("http://localhost:${internalServer.connectors[0].localPort}")
    System.out.println("Started server $this on port ${address.getPort()}")
    return this
  }

  def stop() {
    System.out.println("Stopping server $this on port $address.port")
    internalServer.stop()
    return this
  }

  void close() {
    stop()
  }

  URI getAddress() {
    return address
  }

  def getLastRequest() {
    return last.get()
  }

  void handlers(@DelegatesTo(value = HandlersSpec, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    assert handlers == null: "handlers already defined"
    handlers = new HandlersSpec()

    def clone = (Closure) spec.clone()
    clone.delegate = handlers
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(handlers)
  }

  static distributedRequestTrace(ListWriterAssert traces, int index, DDSpan parentSpan = null) {
    traces.trace(index, 1) {
      span(0) {
        operationName "test-http-server"
        errored false
        if (parentSpan == null) {
          parent()
        } else {
          childOf(parentSpan)
        }
        tags {
          defaultTags(parentSpan != null)
        }
      }
    }
  }

  private class HandlersSpec {

    List<Handler> configured = []

    void get(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.GET, path, spec)
    }

    void post(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.POST, path, spec)
    }

    void put(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      assert path != null
      configured << new HandlerSpec(HttpMethods.PUT, path, spec)
    }

    void prefix(String path, @DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      configured << new PrefixHandlerSpec(path, spec)
    }

    void all(@DelegatesTo(value = HandlerApi, strategy = Closure.DELEGATE_FIRST) Closure<Void> spec) {
      configured << new AllHandlerSpec(spec)
    }
  }

  private class HandlerSpec extends AllHandlerSpec {

    private final String method
    private final String path

    private HandlerSpec(String method, String path, Closure<Void> spec) {
      super(spec)
      this.method = method
      this.path = path.startsWith("/") ? path : "/" + path
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (request.method == method && target == path) {
        send(baseRequest, response)
      }
    }
  }

  private class PrefixHandlerSpec extends AllHandlerSpec {

    private final String prefix

    private PrefixHandlerSpec(String prefix, Closure<Void> spec) {
      super(spec)
      this.prefix = prefix.startsWith("/") ? prefix : "/" + prefix
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      if (target.startsWith(prefix)) {
        send(baseRequest, response)
      }
    }
  }

  private class AllHandlerSpec extends AbstractHandler {
    protected final Closure<Void> spec

    protected AllHandlerSpec(Closure<Void> spec) {
      this.spec = spec
    }

    @Override
    void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
      send(baseRequest, response)
    }

    protected void send(Request baseRequest, HttpServletResponse response) {
      def api = new HandlerApi(baseRequest, response)
      last.set(api.request)

      def clone = (Closure) spec.clone()
      clone.delegate = api
      clone.resolveStrategy = Closure.DELEGATE_FIRST

      try {
        clone(api)
      } catch (Exception e) {
        api.response.status(500).send(e.getMessage())
      }
    }
  }

  class HandlerApi {
    private final Request req
    private final HttpServletResponse resp

    private HandlerApi(Request request, HttpServletResponse response) {
      this.req = request
      this.resp = response
    }

    def getRequest() {
      return new RequestApi()
    }


    def getResponse() {
      return new ResponseApi()
    }

    void redirect(String uri) {
      resp.sendRedirect(uri)
      req.handled = true
    }

    void handleDistributedRequest() {
      boolean isDDServer = true
      if (request.getHeader("is-dd-server") != null) {
        isDDServer = Boolean.parseBoolean(request.getHeader("is-dd-server"))
      }
      if (isDDServer) {
        final SpanContext extractedContext =
          tracer.extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req))
        def builder = tracer
          .buildSpan("test-http-server")
        if (extractedContext != null) {
          builder.asChildOf(extractedContext)
        }
        builder.start().finish()
      }
    }

    class RequestApi {
      def path = req.pathInfo
      def headers = new Headers(req)
      def contentLength = req.contentLength
      def contentType = req.contentType?.split(";")

      def body = req.inputStream.bytes

      def getPath() {
        return path
      }

      def getContentLength() {
        return contentLength
      }

      def getContentType() {
        return contentType ? contentType[0] : null
      }

      def getHeaders() {
        return headers
      }

      String getHeader(String header) {
        return headers[header]
      }

      def getBody() {
        return body
      }

      def getText() {
        return new String(body)
      }
    }

    class ResponseApi {
      private int status = 200

      ResponseApi status(int status) {
        this.status = status
        return this
      }

      void send() {
        assert !req.handled
        req.contentType = "text/plain;charset=utf-8"
        resp.status = status
        req.handled = true
      }

      void send(String body) {
        assert body != null

        send()
        resp.setContentLength(body.bytes.length)
        resp.writer.print(body)
      }
    }

    static class Headers {
      private final Map<String, String> headers

      private Headers(Request request) {
        this.headers = [:]
        request.getHeaderNames().each {
          headers.put(it, request.getHeader(it))
        }
      }

      def get(String header) {
        return headers[header]
      }
    }
  }
}
