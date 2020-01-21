import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import io.dropwizard.testing.ConfigOverride
import io.dropwizard.testing.DropwizardTestSupport
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.jaxrs2.JaxRsAnnotationsDecorator
import io.opentelemetry.auto.instrumentation.servlet3.Servlet3Decorator
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.sdk.trace.SpanData
import org.eclipse.jetty.servlet.ServletHandler

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class DropwizardTest extends HttpServerTest<DropwizardTestSupport, Servlet3Decorator> {

  @Override
  DropwizardTestSupport startServer(int port) {
    println "Port: $port"
    def testSupport = new DropwizardTestSupport(testApp(),
      null,
      ConfigOverride.config("server.applicationConnectors[0].port", "$port"),
      ConfigOverride.config("server.adminConnectors[0].port", PortUtils.randomOpenPort().toString()))
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

  @Override
  Servlet3Decorator decorator() {
    return new Servlet3Decorator() {
      @Override
      protected String component() {
        return "jax-rs"
      }
    }
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testNotFound() {
    false
  }

  boolean testExceptionBody() {
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "jax-rs.request"
      errored endpoint == EXCEPTION
      childOf((SpanData) parent)
      tags {
        "$MoreTags.RESOURCE_NAME" "${this.testResource().simpleName}.${endpoint.name().toLowerCase()}"
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" JaxRsAnnotationsDecorator.DECORATE.component()
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName()
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$MoreTags.RESOURCE_NAME" "$method ${endpoint.resolve(address).path}"
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.PEER_PORT" Long
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        "span.origin.type" ServletHandler.CachedChain.name
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
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
  }

  @Path("/")
  static class ServiceResource extends ParentClass {}
}
