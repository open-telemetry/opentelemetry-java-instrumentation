/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerTracingHandler;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpServerCodec;

/**
 * When certain handlers are added to the pipeline, we want to add our corresponding tracing
 * handlers. If those handlers are later removed, we may want to remove our handlers. That is not
 * currently implemented.
 */
public final class ChannelPipelineUtil {

  public static void wrapHandler(ChannelPipeline pipeline, ChannelHandler handler) {
    // Server pipeline handlers
    if (handler instanceof HttpServerCodec) {
      pipeline.addLast(HttpServerTracingHandler.class.getName(), new HttpServerTracingHandler());
    } else if (handler instanceof HttpRequestDecoder) {
      pipeline.addLast(
          HttpServerRequestTracingHandler.class.getName(), new HttpServerRequestTracingHandler());
    } else if (handler instanceof HttpResponseEncoder) {
      pipeline.addLast(
          HttpServerResponseTracingHandler.class.getName(), new HttpServerResponseTracingHandler());
    } else
    // Client pipeline handlers
    if (handler instanceof HttpClientCodec) {
      pipeline.addLast(HttpClientTracingHandler.class.getName(), new HttpClientTracingHandler());
    } else if (handler instanceof HttpRequestEncoder) {
      pipeline.addLast(
          HttpClientRequestTracingHandler.class.getName(), new HttpClientRequestTracingHandler());
    } else if (handler instanceof HttpResponseDecoder) {
      pipeline.addLast(
          HttpClientResponseTracingHandler.class.getName(), new HttpClientResponseTracingHandler());
    }
  }

  private ChannelPipelineUtil() {}
}
