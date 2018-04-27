package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class HttpServlet2Instrumentation extends Instrumenter.Configurable {
  public static final String SERVLET_OPERATION_NAME = "servlet.request";
  static final HelperInjector SERVLET2_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter",
          "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator");

  public HttpServlet2Instrumentation() {
    super("servlet", "servlet-2");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface()).and(failSafe(hasSuperType(named("javax.servlet.http.HttpServlet")))),
            not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
                .and(
                    classLoaderHasClasses(
                        "javax.servlet.ServletContextEvent", "javax.servlet.FilterChain")))
        .transform(SERVLET2_HELPER_INJECTOR)
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create(false) // Can't use the error handler for pre 1.5 classes...
                .advice(
                    named("service")
                        .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
                        .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
                        .and(isProtected()),
                    HttpServlet2Advice.class.getName()))
        .asDecorator();
  }

  public static class HttpServlet2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(0) final HttpServletRequest req) {
      if (GlobalTracer.get().scopeManager().active() != null) {
        // doFilter is called by each filter. We only want to time outer-most.
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
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .startActive(true);

      final Span span = scope.span();
      Tags.COMPONENT.set(span, "java-web-servlet");
      Tags.HTTP_METHOD.set(span, req.getMethod());
      Tags.HTTP_URL.set(span, req.getRequestURL().toString());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Argument(1) final ServletResponse response,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
          final Span span = scope.span();
          final HttpServletRequest req = (HttpServletRequest) request;
          final HttpServletResponse resp = (HttpServletResponse) response;

          if (throwable != null) {
            Tags.ERROR.set(span, Boolean.TRUE);
            span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
          } else {
            Tags.COMPONENT.set(span, "java-web-servlet");
            Tags.HTTP_METHOD.set(span, req.getMethod());
            Tags.HTTP_URL.set(span, req.getRequestURL().toString());
          }
        }
        scope.close();
      }
    }
  }
}
