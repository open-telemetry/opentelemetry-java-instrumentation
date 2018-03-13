package datadog.trace.instrumentation.jaxrs;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientTracingFeature implements Feature {
  @Override
  public boolean configure(final FeatureContext context) {
    context.register(new ClientTracingFilter());
    log.debug("ClientTracingFilter registered");
    return true;
  }
}
