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
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import org.apache.jasper.JspCompilationContext;

@AutoService(Instrumenter.class)
public final class JasperJSPCompilationContextInstrumentation extends Instrumenter.Configurable {

  public JasperJSPCompilationContextInstrumentation() {
    super("jsp", "jsp-compile");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("org.apache.jasper.JspCompilationContext"),
            classLoaderHasClasses("org.apache.jasper.servlet.JspServletWrapper"))
        .transform(DDTransformers.defaultTransformers())
        .transform(
            DDAdvice.create()
                .advice(
                    named("compile").and(takesArguments(0)).and(isPublic()),
                    JasperJspCompilationContext.class.getName()))
        .asDecorator();
  }

  public static class JasperJspCompilationContext {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.This JspCompilationContext jspCompilationContext) {

      final Scope scope =
          GlobalTracer.get()
              .buildSpan("jsp.compile")
              .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
              .withTag(DDTags.SPAN_TYPE, DDSpanTypes.WEB_SERVLET)
              .withTag(
                  "servlet.context", jspCompilationContext.getServletContext().getContextPath())
              .startActive(true);

      final Span span = scope.span();
      span.setTag(DDTags.RESOURCE_NAME, jspCompilationContext.getJspFile());
      Tags.COMPONENT.set(span, "jsp-http-servlet");
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final JspCompilationContext jspCompilationContext,
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable) {

      final Span span = scope.span();
      if (jspCompilationContext != null) {
        span.setTag("jsp.compiler", jspCompilationContext.getCompiler().getClass().getName());
        span.setTag("jsp.classFQCN", jspCompilationContext.getFQCN());
        if (throwable != null) {
          span.setTag("jsp.javaFile", jspCompilationContext.getServletJavaFileName());
          span.setTag("jsp.classpath", jspCompilationContext.getClassPath());
        }
      }
      if (throwable != null) {
        Tags.ERROR.set(span, Boolean.TRUE);
        span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
      }
      scope.close();
    }
  }
}
