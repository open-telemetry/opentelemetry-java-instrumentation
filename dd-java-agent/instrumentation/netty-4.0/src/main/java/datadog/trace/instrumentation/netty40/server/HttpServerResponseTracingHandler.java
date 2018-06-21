package datadog.trace.instrumentation.netty40.server;

import static io.opentracing.log.Fields.ERROR_OBJECT;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import java.util.Collections;

public class HttpServerResponseTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final Span span = ctx.channel().attr(HttpServerTracingHandler.attributeKey).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpResponse response = (HttpResponse) msg;

    try {
      ctx.write(msg, prm);
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
