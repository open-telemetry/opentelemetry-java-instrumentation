import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.channel.DefaultChannelPipeline
import org.jboss.netty.channel.DownstreamMessageEvent
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.FailedChannelFuture
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelHandler
import org.jboss.netty.channel.SucceededChannelFuture
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpServerCodec
import org.jboss.netty.handler.logging.LoggingHandler
import org.jboss.netty.logging.InternalLogLevel
import org.jboss.netty.util.CharsetUtil

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.forPath
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.LOCATION
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

class Netty38ServerTest extends HttpServerTest<Channel> {

  ChannelPipeline channelPipeline() {
    ChannelPipeline channelPipeline = new DefaultChannelPipeline()

    channelPipeline.addLast("http-codec", new HttpServerCodec())
    channelPipeline.addLast("controller", new SimpleChannelHandler() {
      @Override
      void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) throws Exception {
        if (msg.getMessage() instanceof HttpRequest) {
          def uri = URI.create((msg.getMessage() as HttpRequest).getUri())
          HttpServerTest.ServerEndpoint endpoint = forPath(uri.path)
          ctx.sendDownstream controller(endpoint) {
            HttpResponse response
            ChannelBuffer responseContent = null
            switch (endpoint) {
              case SUCCESS:
              case ERROR:
                responseContent = ChannelBuffers.copiedBuffer(endpoint.body, CharsetUtil.UTF_8)
                response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status))
                response.setContent(responseContent)
                break
              case QUERY_PARAM:
                responseContent = ChannelBuffers.copiedBuffer(uri.query, CharsetUtil.UTF_8)
                response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status))
                response.setContent(responseContent)
                break
              case REDIRECT:
                response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status))
                response.headers().set(LOCATION, endpoint.body)
                break
              case EXCEPTION:
                throw new Exception(endpoint.body)
              default:
                responseContent = ChannelBuffers.copiedBuffer(NOT_FOUND.body, CharsetUtil.UTF_8)
                response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status))
                response.setContent(responseContent)
                break
            }
            response.headers().set(CONTENT_TYPE, "text/plain")
            if (responseContent) {
              response.headers().set(CONTENT_LENGTH, responseContent.readableBytes())
            }
            return new DownstreamMessageEvent(
              ctx.getChannel(),
              new SucceededChannelFuture(ctx.getChannel()),
              response,
              ctx.getChannel().getRemoteAddress())
          }
        }
      }

      @Override
      void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent ex) throws Exception {
        def message = ex.getCause() == null ? "<no cause> " + ex.message : ex.cause.message == null ? "<null>" : ex.cause.message
        ChannelBuffer buffer = ChannelBuffers.copiedBuffer(message, CharsetUtil.UTF_8)
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        response.setContent(buffer)
        response.headers().set(CONTENT_TYPE, "text/plain")
        response.headers().set(CONTENT_LENGTH, buffer.readableBytes())
        ctx.sendDownstream(new DownstreamMessageEvent(
          ctx.getChannel(),
          new FailedChannelFuture(ctx.getChannel(), ex.getCause()),
          response,
          ctx.getChannel().getRemoteAddress()))
      }
    })

    return channelPipeline
  }

  @Override
  Channel startServer(int port) {
    ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory())
    bootstrap.setParentHandler(new LoggingHandler(InternalLogLevel.INFO))
    bootstrap.setPipeline(channelPipeline())

    InetSocketAddress address = new InetSocketAddress(port)
    return bootstrap.bind(address)
  }

  @Override
  void stopServer(Channel server) {
    server?.disconnect()
  }

  @Override
  String component() {
    NettyHttpServerDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    "netty.request"
  }
}
