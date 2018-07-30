package datadog.trace.instrumentation.servlet3;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractServlet3Instrumentation extends Instrumenter.Default {

  public AbstractServlet3Instrumentation() {
    super("servlet", "servlet-3");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("javax.servlet.FilterChain")));
  }

  @Override
  public ElementMatcher<? super ClassLoader> classLoaderMatcher() {
    return classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.servlet3.HttpServletRequestExtractAdapter",
      "datadog.trace.instrumentation.servlet3.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
      "datadog.trace.instrumentation.servlet3.TagSettingAsyncListener"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("doFilter")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        Servlet3Advice.class.getName());
    return transformers;
  }
}
