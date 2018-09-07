package datadog.trace.instrumentation.servlet2;

import datadog.trace.agent.tooling.Instrumenter;

public abstract class AbstractServlet2Instrumentation extends Instrumenter.Default {

  public AbstractServlet2Instrumentation() {
    super("servlet", "servlet-2");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter",
      "datadog.trace.instrumentation.servlet2.HttpServletRequestExtractAdapter$MultivaluedMapFlatIterator"
    };
  }
}
