package datadog.trace.instrumentation.servlet2;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractServlet2Instrumentation extends Instrumenter.Default {

  public AbstractServlet2Instrumentation() {
    super("servlet", "servlet-2");
  }

  // this is required to make sure servlet 2 instrumentation won't apply to servlet 3
  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(classLoaderHasClasses("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ServerDecorator",
      "datadog.trace.agent.decorator.HttpServerDecorator",
      packageName + ".HttpServletRequestExtractAdapter",
      packageName + ".HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator",
      packageName + ".Servlet2Decorator",
    };
  }
}
