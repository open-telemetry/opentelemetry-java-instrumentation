package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class FilterChain2Instrumentation extends Instrumenter.Default {

  public FilterChain2Instrumentation() {
    super("servlet", "servlet-2");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(hasSuperType(named("javax.servlet.FilterChain")));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"))
        .and(
            classLoaderHasClasses(
                "javax.servlet.ServletContextEvent", "javax.servlet.ServletRequest"));
  }

  @Override
  public String[] helperClassNames() {
    return HttpServlet2Instrumentation.HELPERS;
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("doFilter")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        Servlet2Advice.class.getName());
    return transformers;
  }
}
