package com.example.javaagent.instrumentation;

import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.tooling.ClassLoaderMatcher;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers;
import io.opentelemetry.javaagent.tooling.matcher.NameMatchers;
import java.util.Map;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * This is a demo instrumentation which hooks into servlet invocation and modifies the http response.
 */
@AutoService(Instrumenter.class)
public final class DemoServlet3Instrumentation extends Instrumenter.Default {
  public DemoServlet3Instrumentation() {
    super("servlet-demo", "servlet-3");
  }

  /*
  We want this instrumentation to be applied after the standard servlet instrumentation.
  The latter creates a server span around http request.
  This instrumentation needs access to that server span.
   */
  @Override
  public int getOrder() {
    return 1;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return ClassLoaderMatcher.hasClassesNamed("javax.servlet.http.HttpServlet");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.safeHasSuperType(
        NameMatchers.namedOneOf("javax.servlet.FilterChain", "javax.servlet.http.HttpServlet"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        NameMatchers.namedOneOf("doFilter", "service")
            .and(ElementMatchers.takesArgument(0, ElementMatchers.named("javax.servlet.ServletRequest")))
            .and(ElementMatchers.takesArgument(1, ElementMatchers.named("javax.servlet.ServletResponse")))
            .and(ElementMatchers.isPublic()),
        DemoServlet3Instrumentation.class.getName() + "$DemoServlet3Advice");
  }

  @SuppressWarnings("unused")
  public static class DemoServlet3Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 1) ServletResponse response) {
      if (!(response instanceof HttpServletResponse)) {
        return;
      }

      HttpServletResponse httpServletResponse = (HttpServletResponse) response;
      if (!httpServletResponse.containsHeader("X-server-id")) {
        httpServletResponse.addHeader("X-server-id", Span.current().getSpanContext().getTraceIdAsHexString());
      }
    }

  }
}