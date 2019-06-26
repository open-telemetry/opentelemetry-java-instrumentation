package datadog.trace.instrumentation.netty41;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.netty41.client.HttpClientRequestTracingHandler;
import datadog.trace.instrumentation.netty41.client.HttpClientResponseTracingHandler;
import datadog.trace.instrumentation.netty41.client.HttpClientTracingHandler;
import datadog.trace.instrumentation.netty41.server.HttpServerRequestTracingHandler;
import datadog.trace.instrumentation.netty41.server.HttpServerResponseTracingHandler;
import datadog.trace.instrumentation.netty41.server.HttpServerTracingHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.Attribute;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class NettyChannelPipelineInstrumentation extends Instrumenter.Default {

  static final String INSTRUMENTATION_NAME = "netty";
  static final String[] ADDITIONAL_INSTRUMENTATION_NAMES = {"netty-4.1"};

  public NettyChannelPipelineInstrumentation() {
    super(INSTRUMENTATION_NAME, ADDITIONAL_INSTRUMENTATION_NAMES);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("io.netty.channel.ChannelPipeline")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AttributeKeys",
      "datadog.trace.agent.decorator.BaseDecorator",
      // client helpers
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".client.NettyHttpClientDecorator",
      packageName + ".client.NettyResponseInjectAdapter",
      packageName + ".client.HttpClientRequestTracingHandler",
      packageName + ".client.HttpClientResponseTracingHandler",
      packageName + ".client.HttpClientTracingHandler",
      // server helpers
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".server.NettyHttpServerDecorator",
      packageName + ".server.NettyRequestExtractAdapter",
      packageName + ".server.HttpServerRequestTracingHandler",
      packageName + ".server.HttpServerResponseTracingHandler",
      packageName + ".server.HttpServerTracingHandler"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(2, named("io.netty.channel.ChannelHandler"))),
        ChannelPipelineAddAdvice.class.getName());
    transformers.put(
        isMethod().and(named("connect")).and(returns(named("io.netty.channel.ChannelFuture"))),
        ChannelPipelineConnectAdvice.class.getName());
    return transformers;
  }

  /**
   * When certain handlers are added to the pipeline, we want to add our corresponding tracing
   * handlers. If those handlers are later removed, we may want to remove our handlers. That is not
   * currently implemented.
   */
  public static class ChannelPipelineAddAdvice {
    @Advice.OnMethodEnter
    public static int checkDepth(@Advice.Argument(2) final ChannelHandler handler) {
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

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addHandler(
        @Advice.Enter final int depth,
        @Advice.This final ChannelPipeline pipeline,
        @Advice.Argument(2) final ChannelHandler handler) {
      if (depth > 0) {
        return;
      }

      try {
        // Server pipeline handlers
        if (handler instanceof HttpServerCodec) {
          pipeline.addLast(
              HttpServerTracingHandler.class.getName(), new HttpServerTracingHandler());
        } else if (handler instanceof HttpRequestDecoder) {
          pipeline.addLast(
              HttpServerRequestTracingHandler.class.getName(),
              new HttpServerRequestTracingHandler());
        } else if (handler instanceof HttpResponseEncoder) {
          pipeline.addLast(
              HttpServerResponseTracingHandler.class.getName(),
              new HttpServerResponseTracingHandler());
        } else
        // Client pipeline handlers
        if (handler instanceof HttpClientCodec) {
          pipeline.addLast(
              HttpClientTracingHandler.class.getName(), new HttpClientTracingHandler());
        } else if (handler instanceof HttpRequestEncoder) {
          pipeline.addLast(
              HttpClientRequestTracingHandler.class.getName(),
              new HttpClientRequestTracingHandler());
        } else if (handler instanceof HttpResponseDecoder) {
          pipeline.addLast(
              HttpClientResponseTracingHandler.class.getName(),
              new HttpClientResponseTracingHandler());
        }
      } catch (final IllegalArgumentException e) {
        // Prevented adding duplicate handlers.
      } finally {
        CallDepthThreadLocalMap.reset(handler.getClass());
      }
    }
  }

  public static class ChannelPipelineConnectAdvice {
    @Advice.OnMethodEnter
    public static void addParentSpan(@Advice.This final ChannelPipeline pipeline) {
      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope instanceof TraceScope) {
        final TraceScope.Continuation continuation = ((TraceScope) scope).capture();
        if (null != continuation) {
          final Attribute<TraceScope.Continuation> attribute =
              pipeline.channel().attr(AttributeKeys.PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY);
          if (!attribute.compareAndSet(null, continuation)) {
            continuation.close();
          }
        }
      }
    }
  }
}
