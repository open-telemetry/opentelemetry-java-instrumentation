/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.HttpClientTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.server.HttpServerTracingHandler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpServerCodec;

public class NettyChannelPipelineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.jboss.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.ChannelPipeline"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(1, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd2ArgsAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(2, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd3ArgsAdvice");
  }

  /**
   * When certain handlers are added to the pipeline, we want to add our corresponding tracing
   * handlers. If those handlers are later removed, we may want to remove our handlers. That is not
   * currently implemented.
   */
  public static class ChannelPipelineAdviceUtil {
    public static void wrapHandler(ChannelPipeline pipeline, ChannelHandler handler) {
      // Server pipeline handlers
      if (handler instanceof HttpServerCodec) {
        pipeline.addLast(HttpServerTracingHandler.class.getName(), new HttpServerTracingHandler());
      } else if (handler instanceof HttpRequestDecoder) {
        pipeline.addLast(
            HttpServerRequestTracingHandler.class.getName(), new HttpServerRequestTracingHandler());
      } else if (handler instanceof HttpResponseEncoder) {
        pipeline.addLast(
            HttpServerResponseTracingHandler.class.getName(),
            new HttpServerResponseTracingHandler());
      } else
      // Client pipeline handlers
      if (handler instanceof HttpClientCodec) {
        pipeline.addLast(HttpClientTracingHandler.class.getName(), new HttpClientTracingHandler());
      } else if (handler instanceof HttpRequestEncoder) {
        pipeline.addLast(
            HttpClientRequestTracingHandler.class.getName(), new HttpClientRequestTracingHandler());
      } else if (handler instanceof HttpResponseDecoder) {
        pipeline.addLast(
            HttpClientResponseTracingHandler.class.getName(),
            new HttpClientResponseTracingHandler());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAdd2ArgsAdvice {

    @Advice.OnMethodEnter
    public static void checkDepth(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) ChannelHandler handler,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
      callDepth = CallDepth.forClass(ChannelPipeline.class);
      callDepth.getAndIncrement();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) ChannelHandler handler,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      ChannelPipelineAdviceUtil.wrapHandler(pipeline, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAdd3ArgsAdvice {

    @Advice.OnMethodEnter
    public static void checkDepth(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Local("otelCallDepth") CallDepth callDepth) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
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

      ChannelPipelineAdviceUtil.wrapHandler(pipeline, handler);
    }
  }
}
