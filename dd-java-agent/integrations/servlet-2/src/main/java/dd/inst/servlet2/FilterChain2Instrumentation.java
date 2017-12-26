package dd.inst.servlet2;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import dd.trace.DDAdvice;
import dd.trace.HelperInjector;
import dd.trace.Instrumenter;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
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
public final class FilterChain2Instrumentation implements Instrumenter {
  public static final String FILTER_CHAIN_OPERATION_NAME = "servlet.request";

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("javax.servlet.FilterChain"))),
            not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
                .and(
                    classLoaderHasClasses(
                        "javax.servlet.ServletContextEvent", "javax.servlet.ServletRequest")))
        .transform(
            new HelperInjector(
                "io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter",
                "io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
                "io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator",
                "io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator$1",
                "io.opentracing.contrib.web.servlet.filter.TracingFilter",
                "io.opentracing.contrib.web.servlet.filter.TracingFilter$1"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("doFilter")
                        .and(takesArgument(0, named("javax.servlet.ServletRequest")))
                        .and(takesArgument(1, named("javax.servlet.ServletResponse")))
                        .and(isPublic()),
                    FilterChain2Advice.class.getName()))
        .asDecorator();
  }

  public static class FilterChain2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ActiveSpan startSpan(@Advice.Argument(0) final ServletRequest req) {
      if (GlobalTracer.get().activeSpan() != null || !(req instanceof HttpServletRequest)) {
        // doFilter is called by each filter. We only want to time outer-most.
        return null;
      }

      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(
                  Format.Builtin.HTTP_HEADERS,
                  new HttpServletRequestExtractAdapter((HttpServletRequest) req));

      final ActiveSpan span =
          GlobalTracer.get()
              .buildSpan(FILTER_CHAIN_OPERATION_NAME)
              .asChildOf(extractedContext)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .startActive();

      ServletFilterSpanDecorator.STANDARD_TAGS.onRequest((HttpServletRequest) req, span);
      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Argument(1) final ServletResponse response,
        @Advice.Enter final ActiveSpan span,
        @Advice.Thrown final Throwable throwable) {

      if (span != null) {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
          final HttpServletRequest req = (HttpServletRequest) request;
          final HttpServletResponse resp = (HttpServletResponse) response;

          if (throwable != null) {
            ServletFilterSpanDecorator.STANDARD_TAGS.onError(req, resp, throwable, span);
            span.log(Collections.singletonMap("error.object", throwable));
          } else {
            ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(req, resp, span);
          }
        }
        span.deactivate();
      }
    }
  }
}
