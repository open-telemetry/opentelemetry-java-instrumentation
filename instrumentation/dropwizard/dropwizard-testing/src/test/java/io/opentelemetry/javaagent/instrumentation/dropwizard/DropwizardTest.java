/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizard;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.extension.RegisterExtension;

class DropwizardTest extends AbstractHttpServerTest<DropwizardTestSupport<Configuration>> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected DropwizardTestSupport<Configuration> setupServer() {
    DropwizardTestSupport<Configuration> testSupport =
        new DropwizardTestSupport<>(
            testApp(),
            null,
            ConfigOverride.config("server.applicationConnectors[0].port", String.valueOf(port)),
            ConfigOverride.config(
                "server.adminConnectors[0].port", String.valueOf(PortUtils.findOpenPort())));
    testSupport.before();
    return testSupport;
  }

  @Override
  protected void stopServer(DropwizardTestSupport<Configuration> server) {
    server.after();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    // this override is needed because dropwizard reports peer ip as the client ip
    options.setSockPeerAddr(serverEndpoint -> TEST_CLIENT_IP);
    options.setHasHandlerSpan(endpoint -> endpoint != NOT_FOUND);
    options.setHasResponseSpan(endpoint -> endpoint == NOT_FOUND);
    options.setTestPathParam(true);
    options.setResponseCodeOnNonStandardHttpMethod(405);
    options.setExpectedException(new IllegalStateException(EXCEPTION.getBody()));

    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (HttpConstants._OTHER.equals(method)) {
            return getContextPath() + "/*";
          }

          if (NOT_FOUND.equals(endpoint)) {
            return getContextPath() + "/*";
          } else if (PATH_PARAM.equals(endpoint)) {
            return getContextPath() + "/path/{id}/param";
          } else {
            return super.expectedHttpRoute(endpoint, method);
          }
        });
  }

  Class<? extends Application<Configuration>> testApp() {
    return TestApp.class;
  }

  Class<?> testResource() {
    return ServiceResource.class;
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName(testResource().getSimpleName() + "." + getEndpointName(endpoint))
        .hasKind(INTERNAL);
    if (EXCEPTION.equals(endpoint)) {
      span.hasStatus(StatusData.error())
          .hasException(new IllegalStateException(EXCEPTION.getBody()));
    }
    return span;
  }

  private static String getEndpointName(ServerEndpoint endpoint) {
    if (QUERY_PARAM.equals(endpoint)) {
      return "queryParam";
    } else if (PATH_PARAM.equals(endpoint)) {
      return "pathParam";
    } else if (CAPTURE_HEADERS.equals(endpoint)) {
      return "captureHeaders";
    } else if (INDEXED_CHILD.equals(endpoint)) {
      return "indexedChild";
    }
    return endpoint.name().toLowerCase(Locale.ROOT);
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"))
        .hasKind(INTERNAL);
    return span;
  }

  public static class TestApp extends Application<Configuration> {
    public TestApp() {}

    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {}

    @Override
    public void run(Configuration configuration, Environment environment) {
      environment.jersey().register(ServiceResource.class);
    }
  }

  @Path("/ignored1")
  interface TestInterface {}

  @Path("/ignored2")
  public abstract static class AbstractClass implements TestInterface {

    @GET
    @Path("success")
    public Response success() {
      return controller(
          SUCCESS, () -> Response.status(SUCCESS.getStatus()).entity(SUCCESS.getBody()).build());
    }

    @GET
    @Path("query")
    public Response queryParam(@QueryParam("some") String param) {
      return controller(
          QUERY_PARAM,
          () -> Response.status(QUERY_PARAM.getStatus()).entity("some=" + param).build());
    }

    @GET
    @Path("redirect")
    public Response redirect() throws URISyntaxException {
      return controller(
          REDIRECT,
          () ->
              Response.status(REDIRECT.getStatus()).location(new URI(REDIRECT.getBody())).build());
    }
  }

  @Path("/ignored3")
  public static class ParentClass extends AbstractClass {

    @GET
    @Path("error-status")
    public Response error() {
      return controller(
          ERROR, () -> Response.status(ERROR.getStatus()).entity(ERROR.getBody()).build());
    }

    @GET
    @Path("exception")
    public Response exception() throws Exception {
      return controller(
          EXCEPTION,
          () -> {
            throw new IllegalStateException(EXCEPTION.getBody());
          });
    }

    @GET
    @Path("path/{id}/param")
    public Response pathParam(@PathParam("id") int param) {
      return controller(
          PATH_PARAM,
          () -> Response.status(PATH_PARAM.getStatus()).entity(String.valueOf(param)).build());
    }

    @GET
    @Path("child")
    public Response indexedChild(@QueryParam("id") String param) {
      return controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(id -> id.equals("id") ? param : null);
            return Response.status(INDEXED_CHILD.getStatus())
                .entity(INDEXED_CHILD.getBody())
                .build();
          });
    }

    @GET
    @Path("captureHeaders")
    public Response captureHeaders(@HeaderParam("X-Test-Request") String header) {
      return controller(
          CAPTURE_HEADERS,
          () ->
              Response.status(CAPTURE_HEADERS.getStatus())
                  .header("X-Test-Response", header)
                  .entity(CAPTURE_HEADERS.getBody())
                  .build());
    }
  }

  @Path("/")
  public static class ServiceResource extends ParentClass {}
}
