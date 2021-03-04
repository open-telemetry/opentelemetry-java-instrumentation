/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.NettyResponseInjectAdapter.SETTER;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NettyHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, ChannelHandlerContext ctx, HttpRequest request) {
    Span span =
        tracer
            .spanBuilder(spanNameForRequest(request))
            .setSpanKind(CLIENT)
            .setParent(parentContext)
            .startSpan();
    onRequest(span, request);
    NetPeerUtils.INSTANCE.setNetPeer(span, (InetSocketAddress) ctx.channel().remoteAddress());

    Context context = withClientSpan(parentContext, span);
    inject(context, request.headers(), SETTER);
    return context;
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().name();
  }

  @Override
  protected @Nullable String flavor(HttpRequest httpRequest) {
    return httpRequest.protocolVersion().text();
  }

  @Override
  protected URI url(HttpRequest request) throws URISyntaxException {
    URI uri = new URI(request.uri());
    if ((uri.getHost() == null || uri.getHost().equals("")) && request.headers().contains(HOST)) {
      return new URI("http://" + request.headers().get(HOST) + request.uri());
    } else {
      return uri;
    }
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.status().code();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().get(name);
  }

  @Override
  protected TextMapSetter<HttpHeaders> getSetter() {
    return SETTER;
  }

  public boolean shouldStartSpan(Context parentContext, HttpRequest request) {
    if (!super.shouldStartSpan(parentContext)) {
      return false;
    }
    // The AWS SDK uses Netty for asynchronous clients but constructs a request signature before
    // beginning transport. This means we MUST suppress Netty spans we would normally create or
    // they will inject their own trace header, which does not match what was present when the
    // signature was computed, breaking the SDK request completely. We have not found how to
    // cleanly propagate context from the SDK instrumentation, which executes on an application
    // thread, to Netty instrumentation, which executes on event loops. If it's possible, it may
    // require instrumenting internal classes. Using a header which is more or less guaranteed to
    // always exist is arguably more stable.
    if (request.headers().contains("amz-sdk-invocation-id")) {
      return false;
    }
    return true;
  }

  // TODO (trask) how best to prevent people from use this one instead of the above?
  //  should all shouldStartSpan methods take REQUEST so that they can be suppressed by REQUEST
  //  attributes?
  @Override
  @Deprecated
  public boolean shouldStartSpan(Context parentContext) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.netty-4.1";
  }
}
