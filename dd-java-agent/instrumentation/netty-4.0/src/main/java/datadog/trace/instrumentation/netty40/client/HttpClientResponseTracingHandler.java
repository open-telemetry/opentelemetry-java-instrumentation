package datadog.trace.instrumentation.netty40.client;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;

public class HttpClientResponseTracingHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
    final Span span = ctx.channel().attr(HttpClientTracingHandler.attributeKey).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.fireChannelRead(msg);
      return;
    }

    final HttpResponse response = (HttpResponse) msg;

    try {
      ctx.fireChannelRead(msg);
    } catch (final Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      Tags.HTTP_STATUS.set(span, 500);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }

    Tags.HTTP_STATUS.set(span, response.getStatus().code());
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
