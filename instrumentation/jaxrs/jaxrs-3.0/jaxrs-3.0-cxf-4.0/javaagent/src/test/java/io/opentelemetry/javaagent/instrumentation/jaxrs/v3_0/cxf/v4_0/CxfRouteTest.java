/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.model.UserOperation;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CxfRouteTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @MethodSource("routes")
  void recordsRouteForProgrammaticResource(
      String address,
      String resourcePath,
      String operationPath,
      String requestPath,
      String route,
      boolean abort,
      @TempDir Path tempDir)
      throws Exception {
    assertRoute(
        tempDir,
        address,
        requestPath,
        route,
        abort,
        factory -> {
          factory.setModelBeans(
              resource(ItemResource.class, resourcePath, "item", "GET", operationPath));
          factory.setServiceBean(new ItemResource());
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void recordsRouteThroughTwoLocators(boolean abort, @TempDir Path tempDir) throws Exception {
    assertRoute(
        tempDir,
        "/api",
        "/api/items/123/parts/456/detail",
        "/app/restcall/api/items/{id}/parts/{partId}/detail",
        abort,
        factory -> {
          factory.setModelBeansWithServiceClass(
              asList(
                  resource(NestedItems.class, "/items", "item", null, "/{id}"),
                  resource(NestedItem.class, "/unused", "part", null, "/parts/{partId}"),
                  resource(NestedPart.class, "/also-unused", "detail", "GET", "/detail")),
              NestedItems.class);
          factory.setServiceBean(new NestedItems());
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void recordsRouteWhenLocatorReturnsRootClass(boolean abort, @TempDir Path tempDir)
      throws Exception {
    assertRoute(
        tempDir,
        "/api",
        "/api/items/123/detail",
        "/app/restcall/api/items/{id}/detail",
        abort,
        factory -> {
          factory.setStaticSubresourceResolution(true);
          factory.setServiceBean(new ReusedRoot());
        });
  }

  private static UserResource resource(
      Class<?> type, String resourcePath, String method, String verb, String operationPath) {
    UserOperation operation = new UserOperation();
    operation.setName(method);
    operation.setVerb(verb);
    operation.setPath(operationPath);
    UserResource resource = new UserResource();
    resource.setName(type.getName());
    resource.setPath(resourcePath);
    resource.setOperations(singletonList(operation));
    return resource;
  }

  private static void assertRoute(
      Path tempDir,
      String address,
      String requestPath,
      String route,
      boolean abort,
      Consumer<JAXRSServerFactoryBean> configureResource)
      throws Exception {
    Tomcat tomcat = new Tomcat();
    tomcat.setBaseDir(tempDir.toString());
    tomcat.setPort(0);
    cleanup.deferCleanup(tomcat::destroy);
    cleanup.deferCleanup(tomcat::stop);
    var connector = tomcat.getConnector();
    connector.setProperty("address", "127.0.0.1");
    var context = tomcat.addContext("/app", tempDir.toString());
    CXFNonSpringServlet servlet = new CXFNonSpringServlet();
    Tomcat.addServlet(context, "cxf", servlet).setLoadOnStartup(1);
    context.addServletMappingDecoded("/restcall/*", "cxf");
    tomcat.start();

    JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
    factory.setBus(servlet.getBus());
    factory.setAddress(address);
    configureResource.accept(factory);
    if (abort) {
      factory.setProvider(
          (ContainerRequestFilter)
              request -> request.abortWith(Response.status(403).entity("blocked").build()));
    }
    var server = factory.create();
    cleanup.deferCleanup(server::destroy);

    URI uri =
        URI.create("http://127.0.0.1:" + connector.getLocalPort() + "/app/restcall" + requestPath);
    var response =
        HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(abort ? 403 : 200);
    assertThat(response.body()).isEqualTo(abort ? "blocked" : "item");

    testing.waitAndAssertTraces(trace ->
        trace.hasSpansSatisfyingExactly(span ->
            span.hasKind(SpanKind.SERVER).hasName("GET " + route).hasAttribute(HTTP_ROUTE, route)));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-10.0",
        metric ->
            metric
                .hasName("http.server.request.duration")
                .hasHistogramPointsSatisfying(point ->
                    point
                        .hasAttribute(HTTP_ROUTE, route)
                        .hasAttribute(HTTP_RESPONSE_STATUS_CODE, abort ? 403L : 200L)
                        .hasCount(1)));
  }

  private static Stream<Arguments> routes() {
    return Stream.of(
        argumentSet(
            "resource", "/", "/items", "/{id}", "/items/123", "/app/restcall/items/{id}", false),
        argumentSet(
            "resource rejected by filter",
            "/",
            "/items",
            "/{id}",
            "/items/123",
            "/app/restcall/items/{id}",
            true),
        argumentSet(
            "endpoint address",
            "/api",
            "/items",
            "/{id}",
            "/api/items/123",
            "/app/restcall/api/items/{id}",
            false),
        argumentSet(
            "endpoint address rejected by filter",
            "/api",
            "/items",
            "/{id}",
            "/api/items/123",
            "/app/restcall/api/items/{id}",
            true),
        argumentSet("root resource", "/", "/", "/", "/", "/app/restcall", false),
        argumentSet("root resource rejected by filter", "/", "/", "/", "/", "/app/restcall", true));
  }

  public static class ItemResource {
    public String item() {
      return "item";
    }
  }

  public static class NestedItems {
    public NestedItem item() {
      return new NestedItem();
    }
  }

  public static class NestedItem {
    public NestedPart part() {
      return new NestedPart();
    }
  }

  public static class NestedPart {
    public String detail() {
      return "item";
    }
  }

  @jakarta.ws.rs.Path("/items")
  public static class ReusedRoot {
    @jakarta.ws.rs.Path("/{id}")
    public ReusedRoot item() {
      return this;
    }

    @GET
    @jakarta.ws.rs.Path("/detail")
    public String detail() {
      return "item";
    }
  }
}
