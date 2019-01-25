package datadog.trace.instrumentation.netty41.client;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.opentracing.log.Fields.ERROR_OBJECT;

import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.AttributeKeys;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    TraceScope scope = null;
    final TraceScope.Continuation continuation =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY).getAndRemove();
    if (continuation != null) {
      scope = continuation.activate();
    }

    final HttpRequest request = (HttpRequest) msg;
    final InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();

    final Span span =
        GlobalTracer.get()
            .buildSpan("netty.client.request")
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .withTag(Tags.PEER_HOSTNAME.getKey(), remoteAddress.getHostName())
            .withTag(Tags.PEER_PORT.getKey(), remoteAddress.getPort())
            .withTag(Tags.HTTP_METHOD.getKey(), request.method().name())
            .withTag(Tags.HTTP_URL.getKey(), formatUrl(request))
            .withTag(Tags.COMPONENT.getKey(), "netty-client")
            .withTag(DDTags.SPAN_TYPE, DDSpanTypes.HTTP_CLIENT)
            .start();

    // AWS calls are often signed, so we can't add headers without breaking the signature.
    if (!request.headers().contains("amz-sdk-invocation-id")) {
      GlobalTracer.get()
          .inject(
              span.context(), Format.Builtin.HTTP_HEADERS, new NettyResponseInjectAdapter(request));
    }

    ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

    try {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      Tags.ERROR.set(span, Boolean.TRUE);
      span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }

    if (null != scope) {
      scope.close();
    }
  }

  private String formatUrl(final HttpRequest request) {
    try {
      URI uri = new URI(request.uri());
      if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
        uri = new URI("http://" + request.headers().get(HOST) + request.uri());
      }
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (final URISyntaxException e) {
      log.debug("Cannot parse netty uri: {}", request.uri());
      return request.uri();
    }
  }
}
