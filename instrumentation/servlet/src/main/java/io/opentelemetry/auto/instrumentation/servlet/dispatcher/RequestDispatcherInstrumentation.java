package io.opentelemetry.auto.instrumentation.servlet.dispatcher;

import static io.opentelemetry.auto.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RequestDispatcherInstrumentation extends Instrumenter.Default {
  public RequestDispatcherInstrumentation() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator", packageName + ".RequestDispatcherDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.RequestDispatcher")));
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("forward")
            .or(named("include"))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        RequestDispatcherAdvice.class.getName());
  }

  public static class RequestDispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(
        @Advice.Origin("#m") final String method,
        @Advice.This final RequestDispatcher dispatcher,
        @Advice.Local("_originalServletSpan") Object originalServletSpan,
        @Advice.Argument(0) final ServletRequest request) {
      if (!TRACER.getCurrentSpan().getContext().isValid()) {
        // Don't want to generate a new top-level span
        return null;
      }

      final Span span = TRACER.spanBuilder("servlet." + method).startSpan();
      DECORATE.afterStart(span);

      final String target =
          InstrumentationContext.get(RequestDispatcher.class, String.class).get(dispatcher);
      span.setAttribute(MoreTags.RESOURCE_NAME, target);

      // save the original servlet span before overwriting the request attribute, so that it can be
      // restored on method exit
      originalServletSpan = request.getAttribute(SPAN_ATTRIBUTE);

      // this tells the dispatched servlet to use the current span as the parent for its work
      request.setAttribute(SPAN_ATTRIBUTE, span);

      return new SpanWithScope(span, TRACER.withSpan(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Local("_originalServletSpan") final Object originalServletSpan,
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }

      // restore the original servlet span
      // since spanWithScope is non-null here, originalServletSpan must have been set with the prior
      // servlet span (as opposed to remaining unset)
      request.setAttribute(SPAN_ATTRIBUTE, originalServletSpan);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);

      span.end();
      spanWithScope.closeScope();
    }
  }
}
