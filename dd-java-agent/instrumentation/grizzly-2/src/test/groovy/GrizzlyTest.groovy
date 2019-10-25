import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.instrumentation.grizzly.GrizzlyDecorator
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig

import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
import javax.ws.rs.Path
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class GrizzlyTest extends HttpServerTest<HttpServer, GrizzlyDecorator> {

  static {
    System.setProperty("dd.integration.grizzly.enabled", "true")
  }

  @Override
  HttpServer startServer(int port) {
    ResourceConfig rc = new ResourceConfig()
    rc.register(SimpleExceptionMapper)
    rc.register(ServiceResource)
    GrizzlyHttpServerFactory.createHttpServer(new URI("http://localhost:$port"), rc)
  }

  @Override
  void stopServer(HttpServer server) {
    server.stop()
  }

  @Override
  GrizzlyDecorator decorator() {
    return GrizzlyDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "grizzly.request"
  }

  static class SimpleExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    Response toResponse(Throwable exception) {
      if (exception instanceof NotFoundException) {
        return exception.getResponse()
      }
      Response.status(500).entity(exception.message).build()
    }
  }

  @Path("/")
  static class ServiceResource {

    @GET
    @Path("success")
    Response success() {
      controller(SUCCESS) {
        Response.status(SUCCESS.status).entity(SUCCESS.body).build()
      }
    }

    @GET
    @Path("redirect")
    Response redirect() {
      controller(REDIRECT) {
        Response.status(REDIRECT.status).location(new URI(REDIRECT.body)).build()
      }
    }

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
}
