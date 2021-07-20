package io.opentelemetry.instrumentation.testing.junit;

import io.opentelemetry.instrumentation.testing.LibraryTestRunner;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class HttpClientLibraryInstrumentationExtension extends InstrumentationExtension {

  public static InstrumentationExtension create() {
    return new HttpClientLibraryInstrumentationExtension();
  }

  private final HttpClientTestServer server;

  private HttpClientLibraryInstrumentationExtension() {
    super(LibraryTestRunner.instance());

    server = new HttpClientTestServer(getOpenTelemetry());
  }

  @Override
  public void beforeAll(ExtensionContext extensionContext) throws Exception {
    super.beforeAll(extensionContext);
    server.beforeAll(extensionContext);
  }

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    super.beforeEach(extensionContext);
    Object testInstance = extensionContext.getRequiredTestInstance();

    if (!(testInstance instanceof AbstractHttpClientTest)) {
      throw new AssertionError(
          "HttpClientLibraryInstrumentationExtension can only be applied to a subclass of "
              + "AbstractHttpClientTest");
    }

    ((AbstractHttpClientTest<?>) testInstance).setTesting(this, server);
  }

  @Override
  public void afterAll(ExtensionContext extensionContext) throws Exception {
    super.afterAll(extensionContext);
    server.afterAll(extensionContext);
  }
}
