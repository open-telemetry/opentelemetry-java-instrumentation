package io.opentelemetry.instrumentation.httpserver;

import java.util.Collections;

import org.junit.jupiter.api.extension.RegisterExtension;

import com.sun.net.httpserver.Filter;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;

class JdkHttpServerTest extends AbstractJdkHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forLibrary();

  @Override
  protected Filter customFilter() {
    return JdkServerTelemetry.builder(testing.getOpenTelemetry())
        .setCapturedRequestHeaders(
            Collections.singletonList(AbstractHttpServerTest.TEST_REQUEST_HEADER))
        .setCapturedResponseHeaders(
            Collections.singletonList(AbstractHttpServerTest.TEST_RESPONSE_HEADER))
        .build()
        .otelFilter();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    // library instrumentation does not create a span at all
    options.disableTestNonStandardHttpMethod();
  }
}
