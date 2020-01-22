package io.opentelemetry.auto.instrumentation.netty41;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.netty.channel.ChannelFuture;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.instrumentation.netty41.server.NettyHttpServerDecorator;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ChannelFutureListenerInstrumentation extends Instrumenter.Default {

  public ChannelFutureListenerInstrumentation() {
    super(
        NettyChannelPipelineInstrumentation.INSTRUMENTATION_NAME,
        NettyChannelPipelineInstrumentation.ADDITIONAL_INSTRUMENTATION_NAMES);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("io.netty.channel.ChannelFutureListener")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AttributeKeys",
      packageName + ".AttributeKeys$1",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      // client helpers
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".client.NettyHttpClientDecorator",
      packageName + ".client.NettyResponseInjectAdapter",
      packageName + ".client.HttpClientRequestTracingHandler",
      packageName + ".client.HttpClientResponseTracingHandler",
      packageName + ".client.HttpClientTracingHandler",
      // server helpers
      "io.opentelemetry.auto.decorator.ServerDecorator",
      "io.opentelemetry.auto.decorator.HttpServerDecorator",
      packageName + ".server.NettyHttpServerDecorator",
      packageName + ".server.NettyRequestExtractAdapter",
      packageName + ".server.HttpServerRequestTracingHandler",
      packageName + ".server.HttpServerResponseTracingHandler",
      packageName + ".server.HttpServerTracingHandler"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("operationComplete"))
            .and(takesArgument(0, named("io.netty.channel.ChannelFuture"))),
        ChannelFutureListenerInstrumentation.class.getName() + "$OperationCompleteAdvice");
  }

  public static class OperationCompleteAdvice {
    @Advice.OnMethodEnter
    public static AgentScope activateScope(@Advice.Argument(0) final ChannelFuture future) {
      /*
      Idea here is:
       - To return scope only if we have captured it.
       - To capture scope only in case of error.
       */
      final Throwable cause = future.cause();
      if (cause == null) {
        return null;
      }
      final AgentSpan parentSpan =
          future.channel().attr(AttributeKeys.PARENT_CONNECT_SPAN_ATTRIBUTE_KEY).getAndRemove();
      if (parentSpan == null) {
        return null;
      }
      final AgentScope parentScope = activateSpan(parentSpan, false);

      final AgentSpan errorSpan = startSpan("netty.connect").setAttribute(Tags.COMPONENT, "netty");
      try (final AgentScope scope = activateSpan(errorSpan, false)) {
        NettyHttpServerDecorator.DECORATE.onError(errorSpan, cause);
        NettyHttpServerDecorator.DECORATE.beforeFinish(errorSpan);
        errorSpan.finish();
      }

      return parentScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void deactivateScope(@Advice.Enter final AgentScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
