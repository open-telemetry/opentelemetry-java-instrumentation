package datadog.trace.instrumentation.servlet3;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
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
public final class HttpServlet3Instrumentation implements Instrumenter {
  public static final String SERVLET_OPERATION_NAME = "servlet.request";

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("javax.servlet.http.HttpServlet"))),
            classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
        .transform(
            new HelperInjector(
                "io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter",
                "io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
                "io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator",
                "io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator$1",
                "io.opentracing.contrib.web.servlet.filter.TracingFilter",
                "io.opentracing.contrib.web.servlet.filter.TracingFilter$1",
                getClass().getName() + "$HttpServlet3Advice$TagSettingAsyncListener"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("service")
                        .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
                        .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
                        .and(isProtected()),
                    HttpServlet3Advice.class.getName()))
        .asDecorator();
  }

  public static class HttpServlet3Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(0) final HttpServletRequest req) {
      if (GlobalTracer.get().scopeManager().active() != null) {
        // Tracing might already be applied by the FilterChain.  If so ignore this.
        return null;
      }

      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));

      final Scope scope =
          GlobalTracer.get()
              .buildSpan(SERVLET_OPERATION_NAME)
              .asChildOf(extractedContext)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .startActive(false);

      ServletFilterSpanDecorator.STANDARD_TAGS.onRequest(req, scope.span());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) final HttpServletRequest req,
        @Advice.Argument(1) final HttpServletResponse resp,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        final Span span = scope.span();
        if (throwable != null) {
          ServletFilterSpanDecorator.STANDARD_TAGS.onError(req, resp, throwable, span);
          span.log(Collections.singletonMap("error.object", throwable));
          scope.close();
          scope.span().finish(); // Finish the span manually since finishSpanOnClose was false
        } else if (req.isAsyncStarted()) {
          final AtomicBoolean activated = new AtomicBoolean(false);
          // what if async is already finished? This would not be called
          req.getAsyncContext().addListener(new TagSettingAsyncListener(activated, span));
        } else {
          ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(req, resp, span);
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
          try (Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(
                (HttpServletRequest) event.getSuppliedRequest(),
                (HttpServletResponse) event.getSuppliedResponse(),
                span);
          }
        }
      }

      @Override
      public void onTimeout(final AsyncEvent event) throws IOException {
        if (activated.compareAndSet(false, true)) {
          try (Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            ServletFilterSpanDecorator.STANDARD_TAGS.onTimeout(
                (HttpServletRequest) event.getSuppliedRequest(),
                (HttpServletResponse) event.getSuppliedResponse(),
                event.getAsyncContext().getTimeout(),
                span);
          }
        }
      }

      @Override
      public void onError(final AsyncEvent event) throws IOException {
        if (event.getThrowable() != null && activated.compareAndSet(false, true)) {
          try (Scope scope = GlobalTracer.get().scopeManager().activate(span, true)) {
            ServletFilterSpanDecorator.STANDARD_TAGS.onError(
                (HttpServletRequest) event.getSuppliedRequest(),
                (HttpServletResponse) event.getSuppliedResponse(),
                event.getThrowable(),
                span);
            span.log(Collections.singletonMap("error.object", event.getThrowable()));
          }
        }
      }

      @Override
      public void onStartAsync(final AsyncEvent event) throws IOException {}
    }
  }
}
