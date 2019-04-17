package datadog.trace.instrumentation.ratpack;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.ratpack.RatpackServerDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class ServerErrorHandlerInstrumentation extends Instrumenter.Default {

  public ServerErrorHandlerInstrumentation() {
    super("ratpack");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ratpack.exec.Execution")
        .or(not(isInterface()).and(safeHasSuperType(named("ratpack.error.ServerErrorHandler"))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".RatpackServerDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("error").and(takesArgument(1, Throwable.class)), ErrorHandlerAdvice.class.getName());
  }

  public static class ErrorHandlerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void captureThrowable(@Advice.Argument(1) final Throwable throwable) {
      final Span span = GlobalTracer.get().activeSpan();
      if (span != null) {
        DECORATE.onError(span, throwable);
      }
    }
  }
}
