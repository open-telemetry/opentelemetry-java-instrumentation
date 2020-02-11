package datadog.trace.instrumentation.jetty8;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

public class HttpServletRequestExtractAdapter
    implements AgentPropagation.Getter<HttpServletRequest> {

  public static final HttpServletRequestExtractAdapter GETTER =
      new HttpServletRequestExtractAdapter();

  @Override
  public List<String> keys(final HttpServletRequest carrier) {
    return Collections.list(carrier.getHeaderNames());
  }

  @Override
  public String get(final HttpServletRequest carrier, final String key) {
    return carrier.getHeader(key);
  }
}
