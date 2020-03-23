package datadog.trace.instrumentation.netty38;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.implementsInterface;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.context.TraceScope;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

@AutoService(Instrumenter.class)
public class ChannelFutureListenerInstrumentation extends Instrumenter.Default {

  public ChannelFutureListenerInstrumentation() {
    super(
        NettyChannelPipelineInstrumentation.INSTRUMENTATION_NAME,
        NettyChannelPipelineInstrumentation.ADDITIONAL_INSTRUMENTATION_NAMES);
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed(
        "org.jboss.netty.channel.ChannelFutureListener",
        // 3.10: NoSuchMethodError: org.jboss.netty.handler.codec.http.HttpRequest.setHeader
        "org.jboss.netty.channel.StaticChannelPipeline" // Not in 3.10
        );
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.ChannelFutureListener"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ChannelTraceContext",
      packageName + ".ChannelTraceContext$Factory",
      packageName + ".server.NettyHttpServerDecorator",
      packageName + ".server.NettyRequestExtractAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("operationComplete"))
            .and(takesArgument(0, named("org.jboss.netty.channel.ChannelFuture"))),
        ChannelFutureListenerInstrumentation.class.getName() + "$OperationCompleteAdvice");
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "org.jboss.netty.channel.Channel", ChannelTraceContext.class.getName());
  }

  public static class OperationCompleteAdvice {
    @Advice.OnMethodEnter
    public static TraceScope activateScope(@Advice.Argument(0) final ChannelFuture future) {
      /*
      Idea here is:
       - To return scope only if we have captured it.
       - To capture scope only in case of error.
       */
      final Throwable cause = future.getCause();
      if (cause == null) {
        return null;
      }

      final ContextStore<Channel, ChannelTraceContext> contextStore =
          InstrumentationContext.get(Channel.class, ChannelTraceContext.class);

      final TraceScope.Continuation continuation =
          contextStore
              .putIfAbsent(future.getChannel(), ChannelTraceContext.Factory.INSTANCE)
              .getConnectionContinuationAndRemove();
      if (continuation == null) {
        return null;
      }
      final TraceScope parentScope = continuation.activate();

      final AgentSpan errorSpan = startSpan("netty.connect").setTag(Tags.COMPONENT, "netty");
      try (final AgentScope scope = activateSpan(errorSpan, false)) {
        DECORATE.onError(errorSpan, cause);
        DECORATE.beforeFinish(errorSpan);
        errorSpan.finish();
      }

      return parentScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void deactivateScope(@Advice.Enter final TraceScope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
