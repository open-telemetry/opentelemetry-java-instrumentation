import io.netty.channel.AbstractChannel
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.netty41.client.HttpClientTracingHandler
import io.opentelemetry.auto.instrumentation.netty41.client.NettyHttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Response
import spock.lang.Shared

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty41ClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  def clientConfig = DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(TimeUnit.SECONDS.toMillis(10).toInteger())
  @Shared
  AsyncHttpClient asyncHttpClient = asyncHttpClient(clientConfig)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def methodName = "prepare" + method.toLowerCase().capitalize()
    def requestBuilder = asyncHttpClient."$methodName"(uri.toString())
    headers.each { requestBuilder.setHeader(it.key, it.value) }
    def response = requestBuilder.execute(new AsyncCompletionHandler() {
      @Override
      Object onCompleted(Response response) throws Exception {
        callback?.call()
        return response
      }
    }).get()
    return response.statusCode
  }

  @Override
  NettyHttpClientDecorator decorator() {
    return NettyHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    ex.cause instanceof ConnectException
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      def size = traces[0].size()
      trace(0, size) {
        basicSpan(it, 0, "parent", null, null, thrownException)

        // AsyncHttpClient retries across multiple resolved IP addresses (e.g. 127.0.0.1 and 0:0:0:0:0:0:0:1)
        // for up to a total of 10 seconds (default connection time limit)
        for (def i = 1; i < size; i++) {
          span(i) {
            operationName "netty.connect"
            childOf span(0)
            errored true
            tags {
              "$Tags.COMPONENT" "netty"
              "error.type" AbstractChannel.AnnotatedConnectException.name
              "error.stack" String
              "error.msg" ~/Connection refused: localhost\/[0-9.:]+:$UNUSABLE_PORT/
            }
          }
        }
      }
    }

    where:
    method = "GET"
  }

  def "when a handler is added to the netty pipeline we add our tracing handler"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("name", new HttpClientCodec())

    then:
    // The first one returns the removed tracing handler
    pipeline.remove(HttpClientTracingHandler.getName()) != null
  }

  def "when a handler is added to the netty pipeline we add ONLY ONE tracing handler"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("name", new HttpClientCodec())
    // The first one returns the removed tracing handler
    pipeline.remove(HttpClientTracingHandler.getName())
    // There is only one
    pipeline.remove(HttpClientTracingHandler.getName()) == null

    then:
    thrown NoSuchElementException
  }

  def "handlers of different types can be added"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("some_handler", new SimpleHandler())
    pipeline.addLast("a_traced_handler", new HttpClientCodec())

    then:
    // The first one returns the removed tracing handler
    null != pipeline.remove(HttpClientTracingHandler.getName())
    null != pipeline.remove("some_handler")
    null != pipeline.remove("a_traced_handler")
  }

  def "calling pipeline.addLast methods that use overloaded methods does not cause infinite loop"() {
    setup:
    def channel = new EmbeddedChannel()

    when:
    channel.pipeline().addLast(new SimpleHandler(), new OtherSimpleHandler())

    then:
    null != channel.pipeline().remove('Netty41ClientTest$SimpleHandler#0')
    null != channel.pipeline().remove('Netty41ClientTest$OtherSimpleHandler#0')
  }

  def "when a traced handler is added from an initializer we still detect it and add our channel handlers"() {
    // This test method replicates a scenario similar to how reactor 0.8.x register the `HttpClientCodec` handler
    // into the pipeline.

    setup:
    def channel = new EmbeddedChannel()

    when:
    channel.pipeline().addLast(new TracedHandlerFromInitializerHandler())

    then:
    null != channel.pipeline().remove("added_in_initializer")
    null != channel.pipeline().remove(HttpClientTracingHandler.getName())
  }

  def "request with trace annotated method"() {
    given:
    def annotatedClass = new AnnotatedClass()

    when:
    def status = runUnderTrace("parent") {
      annotatedClass.makeRequestUnderTrace(method)
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          childOf(span(0))
          operationName "trace.annotation"
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" "AnnotatedClass.makeRequestUnderTrace"
            "$Tags.COMPONENT" "trace"
          }
        }
        clientSpan(it, 2, span(1), method)
        server.distributedRequestSpan(it, 3, span(2))
      }
    }

    where:
    method << BODY_METHODS
  }

  class AnnotatedClass {
    @Trace
    int makeRequestUnderTrace(String method) {
      return doRequest(method, server.address.resolve("/success"))
    }
  }

  class SimpleHandler implements ChannelHandler {
    @Override
    void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
  }

  class OtherSimpleHandler implements ChannelHandler {
    @Override
    void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
  }

  class TracedHandlerFromInitializerHandler extends ChannelInitializer<Channel> implements ChannelHandler {
    @Override
    protected void initChannel(Channel ch) throws Exception {
      // This replicates how reactor 0.8.x add the HttpClientCodec
      ch.pipeline().addLast("added_in_initializer", new HttpClientCodec())
    }
  }
}
