package datadog.trace.instrumentation.jsp;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.servlet.JspServletWrapper;

@AutoService(Instrumenter.class)
public final class JasperJSPInstrumentation extends Instrumenter.Configurable {

  public JasperJSPInstrumentation() {
    super("jsp", "tomcat-jsp");
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("org.apache.jasper.servlet.JspServletWrapper"),
            classLoaderHasClasses("org.apache.jasper.servlet.JspServlet"))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    named("service")
                        .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
                        .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
                        .and(isPublic()),
                    JasperJSPAdvice.class.getName()))
        .asDecorator();
  }

  public static class JasperJSPAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(
        @Advice.Argument(0) final HttpServletRequest req,
        @Advice.Argument(2) final boolean preCompile) {
      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jsp.service")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag("servlet.context", req.getContextPath())
              .startActive(true);

      final Span span = scope.span();
      span.setTag("jsp.precompile", preCompile);

      Tags.COMPONENT.set(span, "tomcat-jsp-servlet");
      Tags.HTTP_METHOD.set(span, req.getMethod());
      Tags.HTTP_URL.set(span, req.getRequestURL().toString());
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Argument(1) final HttpServletResponse resp,
        @Advice.This JspServletWrapper jspServletWrapper,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      final Span span = scope.span();
      JspCompilationContext jspCompilationContext = jspServletWrapper.getJspEngineContext();
      if (jspCompilationContext != null) {
        span.setTag("jsp.compiler", jspCompilationContext.getCompiler().getClass().getName());
        span.setTag("jsp.outputDir", jspCompilationContext.getOutputDir());
        span.setTag("jsp.classFQCN", jspCompilationContext.getFQCN());
        if (throwable != null) {
          span.setTag("jsp.classpath", jspCompilationContext.getClassPath());
        }
      }

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
