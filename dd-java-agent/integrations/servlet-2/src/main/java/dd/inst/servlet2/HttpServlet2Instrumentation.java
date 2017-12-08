package dd.inst.servlet2;

import static dd.trace.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import dd.trace.DDAdvice;
import dd.trace.Instrumenter;
import io.opentracing.ActiveSpan;
import io.opentracing.SpanContext;
import io.opentracing.contrib.web.servlet.filter.HttpServletRequestExtractAdapter;
import io.opentracing.contrib.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class HttpServlet2Instrumentation implements Instrumenter {
  public static final String SERVLET_OPERATION_NAME = "servlet.request";

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("javax.servlet.http.HttpServlet"),
            not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
                .and(
                    classLoaderHasClasses(
                        "javax.servlet.ServletContextEvent", "javax.servlet.FilterChain")))
        .transform(
            DDAdvice.create()
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
    public static ActiveSpan startSpan(@Advice.Argument(0) final HttpServletRequest req) {

      final SpanContext extractedContext =
          GlobalTracer.get()
              .extract(Format.Builtin.HTTP_HEADERS, new HttpServletRequestExtractAdapter(req));

      final ActiveSpan span =
          GlobalTracer.get()
              .buildSpan(SERVLET_OPERATION_NAME)
              .asChildOf(extractedContext)
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .startActive();

      ServletFilterSpanDecorator.STANDARD_TAGS.onRequest(req, span);
      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(0) final HttpServletRequest req,
        @Advice.Argument(1) final HttpServletResponse resp,
        @Advice.Enter final ActiveSpan span,
        @Advice.Thrown final Throwable throwable) {

      if (span != null) {
        if (throwable != null) {
          ServletFilterSpanDecorator.STANDARD_TAGS.onError(req, resp, throwable, span);
          span.log(Collections.singletonMap("error.object", throwable));
        } else {
          ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(req, resp, span);
        }
        span.deactivate();
      }
    }
  }
}
