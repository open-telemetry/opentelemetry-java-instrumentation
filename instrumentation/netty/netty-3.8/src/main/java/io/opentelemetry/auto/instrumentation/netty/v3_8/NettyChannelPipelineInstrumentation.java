/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.netty.v3_8;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.netty.v3_8.client.HttpClientRequestTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v3_8.client.HttpClientResponseTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v3_8.client.HttpClientTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v3_8.server.HttpServerRequestTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v3_8.server.HttpServerResponseTracingHandler;
import io.opentelemetry.auto.instrumentation.netty.v3_8.server.HttpServerTracingHandler;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpServerCodec;

@AutoService(Instrumenter.class)
public class NettyChannelPipelineInstrumentation extends Instrumenter.Default {

  static final String INSTRUMENTATION_NAME = "netty";
  static final String[] ADDITIONAL_INSTRUMENTATION_NAMES = {"netty-3.8"};

  public NettyChannelPipelineInstrumentation() {
    super(INSTRUMENTATION_NAME, ADDITIONAL_INSTRUMENTATION_NAMES);
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.jboss.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.ChannelPipeline"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AbstractNettyAdvice",
      packageName + ".ChannelTraceContext",
      packageName + ".ChannelTraceContext$Factory",
      NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdviceUtil",
      // Util
      packageName + ".util.CombinedSimpleChannelHandler",
      // client helpers
      packageName + ".client.NettyHttpClientDecorator",
      packageName + ".client.NettyResponseInjectAdapter",
      packageName + ".client.HttpClientRequestTracingHandler",
      packageName + ".client.HttpClientResponseTracingHandler",
      packageName + ".client.HttpClientTracingHandler",
      // server helpers
      packageName + ".server.NettyHttpServerTracer",
      packageName + ".server.NettyRequestExtractAdapter",
      packageName + ".server.HttpServerRequestTracingHandler",
      packageName + ".server.HttpServerResponseTracingHandler",
      packageName + ".server.HttpServerTracingHandler"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(1, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd2ArgsAdvice");
    transformers.put(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(2, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd3ArgsAdvice");
    return transformers;
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "org.jboss.netty.channel.Channel", ChannelTraceContext.class.getName());
  }

  /**
   * When certain handlers are added to the pipeline, we want to add our corresponding tracing
   * handlers. If those handlers are later removed, we may want to remove our handlers. That is not
   * currently implemented.
   */
  public static class ChannelPipelineAdviceUtil {
    public static void wrapHandler(
        final ContextStore<Channel, ChannelTraceContext> contextStore,
        final ChannelPipeline pipeline,
        final ChannelHandler handler) {
      try {
        // Server pipeline handlers
        if (handler instanceof HttpServerCodec) {
          pipeline.addLast(
              HttpServerTracingHandler.class.getName(), new HttpServerTracingHandler(contextStore));
        } else if (handler instanceof HttpRequestDecoder) {
          pipeline.addLast(
              HttpServerRequestTracingHandler.class.getName(),
              new HttpServerRequestTracingHandler(contextStore));
        } else if (handler instanceof HttpResponseEncoder) {
          pipeline.addLast(
              HttpServerResponseTracingHandler.class.getName(),
              new HttpServerResponseTracingHandler(contextStore));
        } else
        // Client pipeline handlers
        if (handler instanceof HttpClientCodec) {
          pipeline.addLast(
              HttpClientTracingHandler.class.getName(), new HttpClientTracingHandler(contextStore));
        } else if (handler instanceof HttpRequestEncoder) {
          pipeline.addLast(
              HttpClientRequestTracingHandler.class.getName(),
              new HttpClientRequestTracingHandler(contextStore));
        } else if (handler instanceof HttpResponseDecoder) {
          pipeline.addLast(
              HttpClientResponseTracingHandler.class.getName(),
              new HttpClientResponseTracingHandler(contextStore));
        }
      } finally {
        CallDepthThreadLocalMap.reset(ChannelPipeline.class);
      }
    }
  }

  public static class ChannelPipelineAdd2ArgsAdvice extends AbstractNettyAdvice {
    @Advice.OnMethodEnter
    public static int checkDepth(
        @Advice.This final ChannelPipeline pipeline,
        @Advice.Argument(1) final ChannelHandler handler) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
      return CallDepthThreadLocalMap.incrementCallDepth(ChannelPipeline.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.Enter final int depth,
        @Advice.This final ChannelPipeline pipeline,
        @Advice.Argument(1) final ChannelHandler handler) {
      if (depth > 0) {
        return;
      }

      ContextStore<Channel, ChannelTraceContext> contextStore =
          InstrumentationContext.get(Channel.class, ChannelTraceContext.class);

      ChannelPipelineAdviceUtil.wrapHandler(contextStore, pipeline, handler);
    }
  }

  public static class ChannelPipelineAdd3ArgsAdvice extends AbstractNettyAdvice {
    @Advice.OnMethodEnter
    public static int checkDepth(
        @Advice.This final ChannelPipeline pipeline,
        @Advice.Argument(2) final ChannelHandler handler) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
      return CallDepthThreadLocalMap.incrementCallDepth(ChannelPipeline.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.Enter final int depth,
        @Advice.This final ChannelPipeline pipeline,
        @Advice.Argument(2) final ChannelHandler handler) {
      if (depth > 0) {
        return;
      }

      ContextStore<Channel, ChannelTraceContext> contextStore =
          InstrumentationContext.get(Channel.class, ChannelTraceContext.class);

      ChannelPipelineAdviceUtil.wrapHandler(contextStore, pipeline, handler);
    }
  }
}
