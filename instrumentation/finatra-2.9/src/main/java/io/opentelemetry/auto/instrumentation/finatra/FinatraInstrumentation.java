package io.opentelemetry.auto.instrumentation.finatra;

import static io.opentelemetry.auto.instrumentation.finatra.FinatraDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.finatra.FinatraDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

@AutoService(Instrumenter.class)
public class FinatraInstrumentation extends Instrumenter.Default {
  public FinatraInstrumentation() {
    super("finatra");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ServerDecorator",
      "io.opentelemetry.auto.decorator.HttpServerDecorator",
      packageName + ".FinatraDecorator",
      FinatraInstrumentation.class.getName() + "$Listener"
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return not(isInterface())
        .and(safeHasSuperType(named("com.twitter.finatra.http.internal.routing.Route")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("handleMatch"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
        FinatraInstrumentation.class.getName() + "$RouteAdvice");
  }

  public static class RouteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope nameSpan(
        @Advice.Argument(0) final Request request,
        @Advice.FieldValue("path") final String path,
        @Advice.FieldValue("clazz") final Class clazz,
        @Advice.Origin final Method method) {

      // Update the parent "netty.request"
      final Span parent = TRACER.getCurrentSpan();
      parent.setAttribute(MoreTags.RESOURCE_NAME, request.method().name() + " " + path);
      parent.setAttribute(Tags.COMPONENT, "finatra");
      parent.updateName("finatra.request");

      final Span span = TRACER.spanBuilder("finatra.controller").startSpan();
      DECORATE.afterStart(span);
      span.setAttribute(MoreTags.RESOURCE_NAME, DECORATE.spanNameForClass(clazz));

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void setupCallback(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Some<Future<Response>> responseOption) {

      if (spanWithScope == null) {
        return;
      }

      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
        return;
      }

      responseOption.get().addEventListener(new Listener(spanWithScope));
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final SpanWithScope spanWithScope;

    public Listener(final SpanWithScope spanWithScope) {
      this.spanWithScope = spanWithScope;
    }

    @Override
    public void onSuccess(final Response response) {
      final Span span = spanWithScope.getSpan();

      // Don't use DECORATE.onResponse because this is the controller span
      if (Config.get().getHttpServerErrorStatuses().contains(DECORATE.status(response))) {
        span.setStatus(Status.UNKNOWN);
      }

      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }

    @Override
    public void onFailure(final Throwable cause) {
      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, cause);
      DECORATE.beforeFinish(span);
      span.end();
      spanWithScope.closeScope();
    }
  }
}
