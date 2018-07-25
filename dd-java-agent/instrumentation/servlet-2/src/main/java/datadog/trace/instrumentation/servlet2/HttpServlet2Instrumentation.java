package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.failSafe;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class HttpServlet2Instrumentation extends Instrumenter.Default {
  static final String[] HELPERS =
      new String[] {
        "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter",
        "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator"
      };

  public HttpServlet2Instrumentation() {
    super("servlet", "servlet-2");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(failSafe(hasSuperType(named("javax.servlet.http.HttpServlet"))));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
        .and(
            classLoaderHasClasses(
                "javax.servlet.ServletContextEvent", "javax.servlet.FilterChain"));
  }

  @Override
  public String[] helperClassNames() {
    return HELPERS;
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("service")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(isProtected()),
        Servlet2Advice.class.getName());
    return transformers;
  }
}
