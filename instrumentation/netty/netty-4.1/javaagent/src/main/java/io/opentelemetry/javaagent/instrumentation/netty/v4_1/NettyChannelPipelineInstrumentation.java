/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.Attribute;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerRequestTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerResponseTracingHandler;
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.server.HttpServerTracingHandler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class NettyChannelPipelineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.channel.ChannelPipeline"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAddAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("remove"))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineRemoveAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove")).and(takesArgument(0, String.class)),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineRemoveByNameAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove")).and(takesArgument(0, Class.class)),
        NettyChannelPipelineInstrumentation.class.getName()
            + "$ChannelPipelineRemoveByClassAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("connect")).and(returns(named("io.netty.channel.ChannelFuture"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineConnectAdvice");
  }

  /**
   * When certain handlers are added to the pipeline, we want to add our corresponding tracing
   * handlers. If those handlers are later removed, we also remove our handlers. Support for
   * replacing handlers and removeFirst/removeLast is currently not implemented.
   */
  public static class ChannelPipelineAddAdvice {
    @Advice.OnMethodEnter
    public static int trackCallDepth(@Advice.Argument(2) ChannelHandler handler) {
      // Previously we used one unique call depth tracker for all handlers, using
      // ChannelPipeline.class as a key.
      // The problem with this approach is that it does not work with netty's
      // io.netty.channel.ChannelInitializer which provides an `initChannel` that can be used to
      // `addLast` other handlers. In that case the depth would exceed 0 and handlers added from
      // initializers would not be considered.
      // Using the specific handler key instead of the generic ChannelPipeline.class will help us
      // both to handle such cases and avoid adding our additional handlers in case of internal
      // calls of `addLast` to other method overloads with a compatible signature.
      return CallDepthThreadLocalMap.incrementCallDepth(handler.getClass());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.Enter int callDepth,
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) String handlerName,
        @Advice.Argument(2) ChannelHandler handler) {
      if (callDepth > 0) {
        return;
      }
      CallDepthThreadLocalMap.reset(handler.getClass());

      String name = handlerName;
      if (name == null) {
        ChannelHandlerContext context = pipeline.context(handler);
        if (context == null) {
          // probably a ChannelInitializer that was used and removed
          // see the comment above in @Advice.OnMethodEnter
          return;
        }
        name = context.name();
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
          pipeline.addAfter(name, ourHandler.getClass().getName(), ourHandler);
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class)
              .putIfAbsent(handler, ourHandler);
        } catch (IllegalArgumentException e) {
          // Prevented adding duplicate handlers.
        }
      }
    }
  }

  public static class ChannelPipelineRemoveAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) ChannelHandler handler) {
      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }

  public static class ChannelPipelineRemoveByNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) String name) {
      ChannelHandler handler = pipeline.get(name);
      if (handler == null) {
        return;
      }

      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }

  public static class ChannelPipelineRemoveByClassAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(0) Class<ChannelHandler> handlerClass) {
      ChannelHandler handler = pipeline.get(handlerClass);
      if (handler == null) {
        return;
      }

      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }

  public static class ChannelPipelineConnectAdvice {
    @Advice.OnMethodEnter
    public static void addParentSpan(@Advice.This ChannelPipeline pipeline) {
      Attribute<Context> attribute = pipeline.channel().attr(AttributeKeys.CONNECT_CONTEXT);
      attribute.compareAndSet(null, Java8BytecodeBridge.currentContext());
    }
  }
}
