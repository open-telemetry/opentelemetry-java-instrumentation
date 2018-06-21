package datadog.trace.instrumentation.jetty8;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class HandlerInstrumentation extends Instrumenter.Configurable {
  public static final String SERVLET_OPERATION_NAME = "jetty.request";

  public HandlerInstrumentation() {
    super("jetty", "jetty-8");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface())
                .and(hasSuperType(named("org.eclipse.jetty.server.Handler")))
                .and(not(named("org.eclipse.jetty.server.handler.HandlerWrapper"))),
            not(classLoaderHasClasses("org.eclipse.jetty.server.AsyncContext")))
        .transform(
            new HelperInjector(
                "datadog.trace.instrumentation.jetty8.HttpServletRequestExtractAdapter",
                "datadog.trace.instrumentation.jetty8.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
                HandlerInstrumentationAdvice.class.getName() + "$TagSettingAsyncListener"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("handle")
                        .and(takesArgument(0, named("java.lang.String")))
                        .and(takesArgument(1, named("org.eclipse.jetty.server.Request")))
                        .and(takesArgument(2, named("javax.servlet.http.HttpServletRequest")))
                        .and(takesArgument(3, named("javax.servlet.http.HttpServletResponse")))
                        .and(isPublic()),
                    HandlerInstrumentationAdvice.class.getName()))
        .asDecorator();
  }

  public static class HandlerInstrumentationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.This final Object source, @Advice.Argument(2) final HttpServletRequest req) {

      if (GlobalTracer.get().activeSpan() != null) {
        // Tracing might already be applied.  If so ignore this.
        return null;
      }

      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));
      final String resourceName = req.getMethod() + " " + source.getClass().getName();
      final Scope scope =
          GlobalTracer.get()
              .buildSpan(SERVLET_OPERATION_NAME)
              .asChildOf(extractedContext)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag("span.origin.type", source.getClass().getName())
              .startActive(false);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      final Span span = scope.span();
      Tags.HTTP_METHOD.set(span, req.getMethod());
      Tags.HTTP_URL.set(span, req.getRequestURL().toString());
      Tags.COMPONENT.set(span, "jetty-handler");
      span.setTag(DDTags.RESOURCE_NAME, resourceName);
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(2) final HttpServletRequest req,
        @Advice.Argument(3) final HttpServletResponse resp,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        final Span span = scope.span();
        if (throwable != null) {
          if (resp.getStatus() == HttpServletResponse.SC_OK) {
            // exception is thrown in filter chain, but status code is incorrect
            Tags.HTTP_STATUS.set(span, 500);
          }
          Tags.ERROR.set(span, Boolean.TRUE);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
          scope.close();
          span.finish(); // Finish the span manually since finishSpanOnClose was false
        } else if (req.isAsyncStarted()) {
          final AtomicBoolean activated = new AtomicBoolean(false);
          // what if async is already finished? This would not be called
          req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
          scope.close();
        } else {
          Tags.HTTP_STATUS.set(span, resp.getStatus());
          scope.close();
          scope.span().finish(); // Finish the span manually since finishSpanOnClose was false
        }
      }
    }

    public static class TagSettingAsyncListener implements AsyncListener {
      private final AtomicBoolean activated;
      private final Span span;

      public TagSettingAsyncListener(final AtomicBoolean activated, final Span span) {
        this.activated = activated;
        this.span = span;
      }

      @Override
      public void onComplete(final AsyncEvent event) throws IOException {
        if (activated.compareAndSet(false, true)) {
          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            Tags.HTTP_STATUS.set(
                span, ((HttpServletResponse) event.getSuppliedResponse()).getStatus());
          }
        }
      }

      @Override
      public void onTimeout(final AsyncEvent event) throws IOException {
        if (activated.compareAndSet(false, true)) {
          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            Tags.ERROR.set(span, Boolean.TRUE);
            span.setTag("timeout", event.getAsyncContext().getTimeout());
          }
        }
      }

      @Override
      public void onError(final AsyncEvent event) throws IOException {
        if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            if (((HttpServletResponse) event.getSuppliedResponse()).getStatus()
                == HttpServletResponse.SC_OK) {
              // exception is thrown in filter chain, but status code is incorrect
              Tags.HTTP_STATUS.set(span, 500);
            }
            Tags.ERROR.set(span, Boolean.TRUE);
            span.log(Collections.singletonMap(ERROR_OBJECT, event.getThrowable()));
          }
        }
      }

      @Override
      public void onStartAsync(final AsyncEvent event) throws IOException {}
    }
  }
}
