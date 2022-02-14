/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.dropwizard.testing.ConfigOverride
import io.dropwizard.testing.DropwizardTestSupport
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.sdk.trace.data.SpanData

import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class DropwizardTest extends HttpServerTest<DropwizardTestSupport> implements AgentTestTrait {

  @Override
  DropwizardTestSupport startServer(int port) {
    println "Port: $port"
    def testSupport = new DropwizardTestSupport(testApp(),
      null,
      ConfigOverride.config("server.applicationConnectors[0].port", "$port"),
      ConfigOverride.config("server.adminConnectors[0].port", PortUtils.findOpenPort().toString()))
    testSupport.before()
    return testSupport
  }

  Class testApp() {
    TestApp
  }

  Class testResource() {
    ServiceResource
  }

  @Override
  void stopServer(DropwizardTestSupport testSupport) {
    testSupport.after()
  }

  // this override is needed because dropwizard reports peer ip as the client ip
  @Override
  String peerIp(ServerEndpoint endpoint) {
    TEST_CLIENT_IP
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    endpoint != NOT_FOUND
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == NOT_FOUND
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint) {
    switch (endpoint) {
      case NOT_FOUND:
        return getContextPath() + "/*"
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      default:
        return super.expectedHttpRoute(endpoint)
    }
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "${this.testResource().simpleName}.${endpoint.name().toLowerCase()}"
      kind INTERNAL
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    sendErrorSpan(trace, index, parent)
  }

  static class TestApp extends Application<Configuration> {
    @Override
    void initialize(Bootstrap<Configuration> bootstrap) {
    }

    @Override
    void run(Configuration configuration, Environment environment) {
      environment.jersey().register(ServiceResource)
    }
  }

  @Path("/ignored1")
  static interface TestInterface {}

  @Path("/ignored2")
  static abstract class AbstractClass implements TestInterface {

    @GET
    @Path("success")
    Response success() {
      controller(SUCCESS) {
        Response.status(SUCCESS.status).entity(SUCCESS.body).build()
      }
    }

    @GET
    @Path("query")
    Response query_param(@QueryParam("some") String param) {
      controller(QUERY_PARAM) {
        Response.status(QUERY_PARAM.status).entity("some=$param".toString()).build()
      }
    }

    @GET
    @Path("redirect")
    Response redirect() {
      controller(REDIRECT) {
        Response.status(REDIRECT.status).location(new URI(REDIRECT.body)).build()
      }
    }
  }

  @Path("/ignored3")
  static class ParentClass extends AbstractClass {

    @GET
    @Path("error-status")
    Response error() {
      controller(ERROR) {
        Response.status(ERROR.status).entity(ERROR.body).build()
      }
    }

    @GET
    @Path("exception")
    Response exception() {
      controller(EXCEPTION) {
        throw new Exception(EXCEPTION.body)
      }
      return null
    }

    @GET
    @Path("path/{id}/param")
    Response path_param(@PathParam("id") int param) {
      controller(PATH_PARAM) {
        Response.status(PATH_PARAM.status).entity(param.toString()).build()
      }
    }

    @GET
    @Path("child")
    Response indexed_child(@QueryParam("id") String param) {
      controller(INDEXED_CHILD) {
        INDEXED_CHILD.collectSpanAttributes { it == "id" ? param : null }
        Response.status(INDEXED_CHILD.status).entity(INDEXED_CHILD.body).build()
      }
    }

    @GET
    @Path("captureHeaders")
    Response capture_headers(@HeaderParam("X-Test-Request") String header) {
      controller(CAPTURE_HEADERS) {
        Response.status(CAPTURE_HEADERS.status)
          .header("X-Test-Response", header)
          .entity(CAPTURE_HEADERS.body)
          .build()
      }
    }
  }

  @Path("/")
  static class ServiceResource extends ParentClass {}
}
