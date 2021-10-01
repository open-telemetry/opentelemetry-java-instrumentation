/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.netty.common.AbstractNettyChannelPipelineInstrumentation;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.HttpClientRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.HttpClientResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.HttpClientTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.HttpServerRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.HttpServerResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.HttpServerTracingHandler;
import net.bytebuddy.asm.Advice;

public class NettyChannelPipelineInstrumentation
    extends AbstractNettyChannelPipelineInstrumentation {

  @Override
  public void transform(TypeTransformer transformer) {
    super.transform(transformer);

    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add").or(named("replace")))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAddAdvice");
  }

  /**
   * When certain handlers are added to the pipeline, we want to add our corresponding tracing
   * handlers. If those handlers are later removed, we may want to remove our handlers. That is not
   * currently implemented.
   */
  @SuppressWarnings("unused")
  public static class ChannelPipelineAddAdvice {

    @Advice.OnMethodEnter
    public static void trackCallDepth(@Advice.Local("otelCallDepth") CallDepth callDepth) {
      callDepth = CallDepth.forClass(ChannelPipeline.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      ChannelHandler ourHandler = null;
      // Server pipeline handlers
      if (handler instanceof HttpServerCodec) {
        ourHandler = new HttpServerTracingHandler();
      } else if (handler instanceof HttpRequestDecoder) {
        ourHandler = new HttpServerRequestTracingHandler();
      } else if (handler instanceof HttpResponseEncoder) {
        ourHandler = new HttpServerResponseTracingHandler();
        // Client pipeline handlers
      } else if (handler instanceof HttpClientCodec) {
        ourHandler = new HttpClientTracingHandler();
      } else if (handler instanceof HttpRequestEncoder) {
        ourHandler = new HttpClientRequestTracingHandler();
      } else if (handler instanceof HttpResponseDecoder) {
        ourHandler = new HttpClientResponseTracingHandler();
      }

      if (ourHandler != null) {
        try {
          pipeline.addLast(ourHandler.getClass().getName(), ourHandler);
          // associate our handle with original handler so they could be removed together
          VirtualField.find(ChannelHandler.class, ChannelHandler.class)
              .setIfNull(handler, ourHandler);
        } catch (IllegalArgumentException e) {
          // Prevented adding duplicate handlers.
        }
      }
    }
  }
}
