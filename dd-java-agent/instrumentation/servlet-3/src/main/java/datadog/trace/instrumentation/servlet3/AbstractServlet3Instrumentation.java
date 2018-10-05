package datadog.trace.instrumentation.servlet3;

import datadog.trace.agent.tooling.Instrumenter;

public abstract class AbstractServlet3Instrumentation extends Instrumenter.Default {

  public AbstractServlet3Instrumentation() {
    super("servlet", "servlet-3");
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
