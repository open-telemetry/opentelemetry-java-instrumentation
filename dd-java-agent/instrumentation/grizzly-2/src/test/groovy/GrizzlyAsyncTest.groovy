import datadog.trace.agent.test.asserts.ListWriterAssert
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Response
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class GrizzlyAsyncTest extends GrizzlyTest {

  @Override
  HttpServer startServer(int port) {
    ResourceConfig rc = new ResourceConfig()
    rc.register(SimpleExceptionMapper)
    rc.register(ServiceResource)
    GrizzlyHttpServerFactory.createHttpServer(new URI("http://localhost:$port"), rc)
  }

  @Path("/")
  static class ServiceResource {
    private ExecutorService executor = Executors.newSingleThreadExecutor()

    @GET
    @Path("success")
    void success(@Suspended AsyncResponse ar) {
      executor.execute {
        controller(SUCCESS) {
          ar.resume(Response.status(SUCCESS.status).entity(SUCCESS.body).build())
        }
      }
    }

    @GET
    @Path("redirect")
    void redirect(@Suspended AsyncResponse ar) {
      executor.execute {
        controller(REDIRECT) {
          ar.resume(Response.status(REDIRECT.status).location(new URI(REDIRECT.body)).build())
        }
      }
    }

    @GET
    @Path("error")
    void error(@Suspended AsyncResponse ar) {
      executor.execute {
        controller(ERROR) {
          ar.resume(Response.status(ERROR.status).entity(ERROR.body).build())
        }
      }
    }

    @GET
    @Path("exception")
    void exception(@Suspended AsyncResponse ar) {
      executor.execute {
        try {
          controller(EXCEPTION) {
            throw new Exception(EXCEPTION.body)
          }
        } catch (Exception e) {
          ar.resume(e)
        }
      }
    }
  }

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)

    // AsyncResponse.resume closes the handler span before the controller returns, so we need to manually reorder it.
    TEST_WRITER.each {
      def controllerSpan = it.find {
        it.operationName == "controller"
      }
      if (controllerSpan) {
        it.remove(controllerSpan)
        it.add(controllerSpan)
      }
    }
    super.cleanAndAssertTraces(size, spec)
  }
}
