package datadog.trace.instrumentation.servlet3;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;

import datadog.trace.agent.tooling.Instrumenter;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractServlet3Instrumentation extends Instrumenter.Default {

  public AbstractServlet3Instrumentation() {
    super("servlet", "servlet-3");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
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
}
