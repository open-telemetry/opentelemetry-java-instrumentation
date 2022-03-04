/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import grails.boot.GrailsApp;
import grails.boot.config.GrailsAutoConfiguration;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ConfigurableApplicationContext;

public class GrailsTest extends AbstractHttpServerTest<ConfigurableApplicationContext> {

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
    options.setContextPath(getContextPath());
    options.setHasHandlerSpan(unused -> true);
    options.setHasResponseSpan(
        endpoint -> endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND);
    options.setHasErrorPageSpans(
        endpoint -> endpoint == ERROR || endpoint == EXCEPTION || endpoint == NOT_FOUND);
    options.setTestPathParam(true);
  }

  @SpringBootApplication
  static class TestApplication extends GrailsAutoConfiguration {
    static ConfigurableApplicationContext start(int port, String contextPath) {
      GrailsApp grailsApp = new GrailsApp(TestApplication.class);
      // context path configuration property name changes between spring boot versions
      String contextPathKey;
      try {
        Method method = ServerProperties.class.getDeclaredMethod("getServlet");
        contextPathKey = "server.servlet.contextPath";
      } catch (NoSuchMethodException ignore) {
        contextPathKey = "server.context-path";
      }
      Map<String, Object> properties = new HashMap<>();
      properties.put("server.port", port);
      properties.put(contextPathKey, contextPath);
      grailsApp.setDefaultProperties(properties);
      return grailsApp.run();
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

  private static String getContextPath() {
    return "/xyz";
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/test/path";
      case QUERY_PARAM:
        return getContextPath() + "/test/query";
      case ERROR:
        return getContextPath() + "/test/error";
      case NOT_FOUND:
        return getContextPath() + "/**";
      default:
        return getContextPath() + "/test" + endpoint.getPath();
    }
  }

  ConfigurableApplicationContext startServer(int port) {
    return TestApplication.start(port, getContextPath());
  }

  private static String getHandlerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case QUERY_PARAM:
        return "TestController.query";
      case PATH_PARAM:
        return "TestController.path";
      case CAPTURE_HEADERS:
        return "TestController.captureHeaders";
      case INDEXED_CHILD:
        return "TestController.child";
      case NOT_FOUND:
        return "ResourceHttpRequestHandler.handleRequest";
      default:
        return "TestController." + endpoint.name().toLowerCase(Locale.ROOT);
    }
  }

  @Override
  public SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName(getHandlerSpanName(endpoint)).hasKind(SpanKind.INTERNAL);
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasException(new Exception(EXCEPTION.getBody()));
    }
    return span;
  }

  @Override
  public SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    if (endpoint == REDIRECT) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"));
    } else {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"));
    }
    span.hasKind(SpanKind.INTERNAL).hasAttributesSatisfying(attrs -> assertThat(attrs).isEmpty());
    return span;
  }

  @Override
  public List<Consumer<SpanDataAssert>> errorPageSpanAssertions(
      String method, ServerEndpoint endpoint) {
    List<Consumer<SpanDataAssert>> spanAssertions = new ArrayList<>();
    spanAssertions.add(
        span -> {
          span.hasName(endpoint == NOT_FOUND ? "ErrorController.notFound" : "ErrorController.index")
              .hasKind(SpanKind.INTERNAL)
              .hasAttributesSatisfying(attrs -> assertThat(attrs).isEmpty());
        });
    if (endpoint == NOT_FOUND) {
      spanAssertions.add(
          span -> {
            span.satisfies(
                    spanData -> Assertions.assertThat(spanData.getName()).endsWith(".sendError"))
                .hasKind(SpanKind.INTERNAL)
                .hasAttributesSatisfying(attrs -> assertThat(attrs).isEmpty());
          });
    }
    return spanAssertions;
  }
}
