/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1.client;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.AbstractNettyHttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.netty.common.client.NettyHttpClientTracerAccess;

public class NettyHttpClientTracer extends AbstractNettyHttpClientTracer<NettyRequestWrapper> {
  private static final NettyHttpClientTracer TRACER = new NettyHttpClientTracer();

  static {
    NettyHttpClientTracerAccess.setTracer(TRACER);
  }

  private NettyHttpClientTracer() {}

  public static NettyHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.status().code();
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
    return "io.opentelemetry.netty-4.1";
  }
}
