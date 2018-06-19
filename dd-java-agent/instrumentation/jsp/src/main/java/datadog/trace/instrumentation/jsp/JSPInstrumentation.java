package datadog.trace.instrumentation.jsp;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class JSPInstrumentation extends Instrumenter.Configurable {

  public JSPInstrumentation() {
    super("jsp", "jsp-render");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named("javax.servlet.jsp.HttpJspPage"))))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    named("_jspService")
                        .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
                        .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
                        .and(isPublic()),
                    HttpJspPageAdvice.class.getName()))
        .asDecorator();
  }

  public static class HttpJspPageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(0) final HttpServletRequest req) {
      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jsp.render")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag("servlet.context", req.getContextPath())
              .startActive(true);

      final Span span = scope.span();
      // get the JSP file name being rendered in an include action
      Object includeServletPath = req.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
      String resourceName = req.getServletPath();
      if (includeServletPath != null && includeServletPath instanceof String) {
        resourceName = includeServletPath.toString();
      }
      span.setTag(DDTags.RESOURCE_NAME, resourceName);

      Object forwardOrigin = req.getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
      if (forwardOrigin != null && forwardOrigin instanceof String) {
        span.setTag("jsp.forwardOrigin", forwardOrigin.toString());
      }

      // add the request URL as a tag to provide better context when looking at spans produced by
      // actions
      span.setTag("jsp.requestURL", req.getRequestURL().toString());
      Tags.COMPONENT.set(span, "jsp-http-servlet");
      Tags.HTTP_METHOD.set(span, req.getMethod());

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(1) final HttpServletResponse resp,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      final Span span = scope.span();
      if (throwable != null) {
        if (resp.getStatus() == HttpServletResponse.SC_OK) {
          // exception is thrown in filter chain, but status code is incorrect
          Tags.HTTP_STATUS.set(span, 500);
        }
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      } else {
        Tags.HTTP_STATUS.set(span, resp.getStatus());
      }
      scope.close();
    }
  }
}
