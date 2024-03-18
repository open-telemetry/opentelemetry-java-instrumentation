package test.filter;

import filter.AbstractServletFilterTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import test.boot.SecurityConfig;

public class ServletFilterTest extends AbstractServletFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

  @Override
  protected Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected Class<?> filterConfigClass() {
    return ServletFilterConfig.class;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps ? 500 : 200);
  }
}
