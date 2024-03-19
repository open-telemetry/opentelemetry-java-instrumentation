import com.google.common.collect.ImmutableMap;
import filter.AbstractServletFilterTest;
import filter.FilteredAppConfig;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.SemanticAttributes.EXCEPTION_EVENT_NAME;
import static io.opentelemetry.semconv.SemanticAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.SemanticAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.SemanticAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

public class ServletFilterTest extends AbstractServletFilterTest {
  static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");
  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Class<?> securityConfigClass() {
    return SecurityConfig.class;
  }

  @Override
  protected Class<?> filterConfigClass() {
    return ServletFilterConfig.class;
  }

  @Override
  protected ConfigurableApplicationContext setupServer() {
    SpringApplication app = new SpringApplication(FilteredAppConfig.class, securityConfigClass(), filterConfigClass());
    app.setDefaultProperties(ImmutableMap.of(
        "server.port", port,
        "server.error.include-message", "always"));
    return app.run();
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (testLatestDeps && endpoint == ServerEndpoint.NOT_FOUND) {
      String handlerSpanName = "ResourceHttpRequestHandler.handleRequest";
      span.hasName(handlerSpanName)
          .hasKind(SpanKind.INTERNAL)
          .hasStatus(StatusData.error())
          .hasEventsSatisfyingExactly(
            event ->
                event
                    .hasName(EXCEPTION_EVENT_NAME)
                    .hasAttributesSatisfyingExactly(
                        equalTo(EXCEPTION_TYPE, "org.springframework.web.servlet.resource.NoResourceFoundException"),
                        equalTo(EXCEPTION_MESSAGE, EXCEPTION.getBody()),
                        satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
      return span;
    } else {
      return super.assertHandlerSpan(span, method, endpoint);
    }
  }

  @Override
  protected SpanDataAssert assertResponseSpan(SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (testLatestDeps && endpoint == ServerEndpoint.NOT_FOUND) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
      span.hasKind(SpanKind.INTERNAL);
        // not verifying the parent span, in the latest version the responseSpan is the child of the SERVER span, not the handler span
      return span;
    } else {
      return super.assertResponseSpan(span, method, endpoint);
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setResponseCodeOnNonStandardHttpMethod(400);
  }
}
