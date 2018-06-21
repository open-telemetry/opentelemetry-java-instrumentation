package datadog.trace.instrumentation.netty41.client;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.InetSocketAddress;
import java.util.Collections;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }
    final HttpRequest request = (HttpRequest) msg;
    final InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();

    String url = request.uri();
    if (request.headers().contains(HOST)) {
      url = "http://" + request.headers().get(HOST) + url;
    }
    final Span span =
        GlobalTracer.get()
            .buildSpan("netty.client.request")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.PEER_HOSTNAME.getKey(), remoteAddress.getHostName())
            .withTag(Tags.PEER_PORT.getKey(), remoteAddress.getPort())
            .withTag(Tags.HTTP_METHOD.getKey(), request.method().name())
            .withTag(Tags.HTTP_URL.getKey(), url)
            .withTag(Tags.COMPONENT.getKey(), "netty-client")
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
            .start();

    GlobalTracer.get()
        .inject(
            span.context(), Format.Builtin.HTTP_HEADERS, new NettyResponseInjectAdapter(request));

    ctx.channel().attr(HttpClientTracingHandler.attributeKey).set(span);

    try {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }
  }
}
