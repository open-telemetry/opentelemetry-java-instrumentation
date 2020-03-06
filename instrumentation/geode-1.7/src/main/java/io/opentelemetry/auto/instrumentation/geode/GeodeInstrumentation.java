package io.opentelemetry.auto.instrumentation.geode;

import static io.opentelemetry.auto.instrumentation.geode.GeodeDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.geode.GeodeDecorator.TRACER;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.geode.cache.Region;

@AutoService(Instrumenter.class)
public class GeodeInstrumentation extends Instrumenter.Default {
  public GeodeInstrumentation() {
    super("geode", "geode-client");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return hasInterface(named("org.apache.geode.cache.Region"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> map = new HashMap<>(2);
    map.put(
        isMethod()
            .and(
                named("clear")
                    .or(nameStartsWith("contains"))
                    .or(named("create"))
                    .or(named("destroy"))
                    .or(named("entrySet"))
                    .or(
                        named("get")
                            .or(named("getAll"))
                            .or(named("invalidate"))
                            .or(nameStartsWith("put").or(nameStartsWith("remove"))))),
        GeodeInstrumentation.class.getName() + "$RegionAdvice");
    return map;
  }

  public static class RegionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.This final Region thiz, @Advice.Origin final Method method) {
      if (CallDepthThreadLocalMap.incrementCallDepth(RegionAdvice.class) > 0) {
        return null;
      }
      final Span span = TRACER.spanBuilder(method.getName()).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      try {
        if (spanWithScope == null) {
          return;
        }
        final Span span = spanWithScope.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
      } finally {
        CallDepthThreadLocalMap.reset(RegionAdvice.class);
      }
    }
  }
}
