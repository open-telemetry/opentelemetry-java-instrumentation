/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import grails.boot.GrailsApp;
import grails.boot.config.GrailsAutoConfiguration;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ConfigurableApplicationContext;

public class GrailsTest extends AbstractHttpServerTest<ConfigurableApplicationContext> {

  static final boolean testLatestDeps = Boolean.getBoolean("testLatestDeps");

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ConfigurableApplicationContext setupServer() {
    return startServer(port);
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath("/xyz");
    options.setHasHandlerSpan(unused -> true);
    options.setHasResponseSpan(
        endpoint -> endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND);
    options.setHasErrorPageSpans(
        endpoint -> endpoint == ERROR || endpoint == EXCEPTION || endpoint == NOT_FOUND);
    options.setTestPathParam(true);
    options.setResponseCodeOnNonStandardHttpMethod(testLatestDeps ? 200 : 501);
  }

  @SpringBootApplication
  static class TestApplication extends GrailsAutoConfiguration {
    static ConfigurableApplicationContext start(int port, String contextPath) {
      GrailsApp grailsApp = new GrailsApp(TestApplication.class);
      Map<String, Object> properties = new HashMap<>();
      properties.put("server.port", port);
      properties.put(getContextPathKey(), contextPath);
      grailsApp.setDefaultProperties(properties);
      return grailsApp.run();
    }

    @SuppressWarnings("ReturnValueIgnored")
    private static String getContextPathKey() {
      // context path configuration property name changes between spring boot versions
      try {
        ServerProperties.class.getDeclaredMethod("getServlet");
        return "server.servlet.contextPath";
      } catch (NoSuchMethodException ignore) {
        return "server.context-path";
      }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Collection<Class> classes() {
      // java compiler does not see groovy classes
      return Arrays.asList(
          load("test.TestController"), load("test.ErrorController"), load("test.UrlMappings"));
    }

    private static Class<?> load(String name) {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (HttpConstants._OTHER.equals(method)) {
      return testLatestDeps
          ? getContextPath() + "/test" + endpoint.getPath()
          : getContextPath() + "/*";
    }
    if (PATH_PARAM.equals(endpoint)) {
      return getContextPath() + "/test/path";
    } else if (QUERY_PARAM.equals(endpoint)) {
      return getContextPath() + "/test/query";
    } else if (ERROR.equals(endpoint)) {
      return getContextPath() + "/test/error";
    } else if (NOT_FOUND.equals(endpoint)) {
      return getContextPath() + "/**";
    }
    return getContextPath() + "/test" + endpoint.getPath();
  }

  ConfigurableApplicationContext startServer(int port) {
    return TestApplication.start(port, getContextPath());
  }

  private static String getHandlerSpanName(ServerEndpoint endpoint) {
    if (QUERY_PARAM.equals(endpoint)) {
      return "TestController.query";
    } else if (PATH_PARAM.equals(endpoint)) {
      return "TestController.path";
    } else if (CAPTURE_HEADERS.equals(endpoint)) {
      return "TestController.captureHeaders";
    } else if (INDEXED_CHILD.equals(endpoint)) {
      return "TestController.child";
    } else if (NOT_FOUND.equals(endpoint)) {
      return "ResourceHttpRequestHandler.handleRequest";
    }
    return "TestController." + endpoint.name().toLowerCase(Locale.ROOT);
  }

  @Override
  public SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName(getHandlerSpanName(endpoint)).hasKind(SpanKind.INTERNAL);
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasException(new IllegalStateException(EXCEPTION.getBody()));
    }
    return span;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName;
    if (endpoint == REDIRECT) {
      methodName = "sendRedirect";
    } else if (endpoint == ERROR || endpoint == NOT_FOUND) {
      methodName = "sendError";
    } else {
      throw new AssertionError("Unexpected endpoint: " + endpoint.name());
    }
    span.hasKind(SpanKind.INTERNAL)
        .satisfies(spanData -> assertThat(spanData.getName()).endsWith("." + methodName))
        .hasAttributesSatisfyingExactly(
            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName),
            satisfies(CodeIncubatingAttributes.CODE_NAMESPACE, AbstractStringAssert::isNotEmpty));
    return span;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public List<Consumer<SpanDataAssert>> errorPageSpanAssertions(
      String method, ServerEndpoint endpoint) {
    List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
    spanAssertions.add(
        span ->
            span.hasName(
                    endpoint == NOT_FOUND ? "ErrorController.notFound" : "ErrorController.index")
                .hasKind(SpanKind.INTERNAL)
                .hasAttributes(Attributes.empty()));
    if (endpoint == NOT_FOUND) {
      spanAssertions.add(
          span ->
              span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"))
                  .hasKind(SpanKind.INTERNAL)
                  .hasAttributesSatisfyingExactly(
                      equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "sendError"),
                      satisfies(
                          CodeIncubatingAttributes.CODE_NAMESPACE,
                          AbstractStringAssert::isNotEmpty)));
    }
    return spanAssertions;
  }
}
