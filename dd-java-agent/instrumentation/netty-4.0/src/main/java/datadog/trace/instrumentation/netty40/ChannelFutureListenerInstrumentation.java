package datadog.trace.instrumentation.netty40;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.netty.channel.ChannelFuture;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ChannelFutureListenerInstrumentation extends Instrumenter.Default {

  public ChannelFutureListenerInstrumentation() {
    super("netty", "netty-4.0");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("io.netty.channel.ChannelFutureListener")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".AttributeKeys"};
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("operationComplete"))
            .and(takesArgument(0, named("io.netty.channel.ChannelFuture"))),
        OperationCompleteAdvice.class.getName());
    return transformers;
  }

  public static class OperationCompleteAdvice {
    @Advice.OnMethodEnter
    public static TraceScope activateScope(@Advice.Argument(0) final ChannelFuture future) {
      final TraceScope.Continuation continuation =
          future.channel().attr(AttributeKeys.PARENT_CONNECT_CONTINUATION_ATTRIBUTE_KEY).get();

      if (continuation == null) {
        return null;
      }
      final TraceScope scope = continuation.activate();

      final Throwable cause = future.cause();
      if (cause != null) {
        final Span errorSpan =
            GlobalTracer.get()
                .buildSpan("netty.connect")
                .withTag(Tags.COMPONENT.getKey(), "netty")
                .start();
        Tags.ERROR.set(errorSpan, true);
        errorSpan.log(Collections.singletonMap(ERROR_OBJECT, cause));
        errorSpan.finish();
      }

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void deactivateScope(
        @Advice.Enter final TraceScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        ((Scope) scope).close();
      }
    }
  }
}
