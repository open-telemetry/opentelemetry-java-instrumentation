package test.boot;

import boot.AbstractSpringBootBasedTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

  @Override
  public Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps ? 500 : 200);
  }
}
