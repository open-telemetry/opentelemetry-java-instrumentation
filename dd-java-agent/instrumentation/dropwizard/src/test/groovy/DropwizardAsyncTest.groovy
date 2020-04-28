import io.dropwizard.Application
import io.dropwizard.Configuration
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment

import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Response
import java.util.concurrent.Executors

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class DropwizardAsyncTest extends DropwizardTest {

  Class testApp() {
    AsyncTestApp
  }

  Class testResource() {
    AsyncServiceResource
  }

  static class AsyncTestApp extends Application<Configuration> {
    @Override
    void initialize(Bootstrap<Configuration> bootstrap) {
    }

    @Override
    void run(Configuration configuration, Environment environment) {
      environment.jersey().register(AsyncServiceResource)
    }
  }

  @Override
  // Return the handler span's name
  String reorderHandlerSpan() {
    "jax-rs.request"
  }

  @Path("/")
  static class AsyncServiceResource {
    final executor = Executors.newSingleThreadExecutor()

    @GET
    @Path("success")
    void success(@Suspended final AsyncResponse asyncResponse) {
      executor.execute {
        controller(SUCCESS) {
          asyncResponse.resume(Response.status(SUCCESS.status).entity(SUCCESS.body).build())
        }
      }
    }

    @GET
    @Path("query")
    Response query_param(@QueryParam("some") String param, @Suspended final AsyncResponse asyncResponse) {
      executor.execute {
        controller(QUERY_PARAM) {
          asyncResponse.resume(Response.status(QUERY_PARAM.status).entity("some=$param".toString()).build())
        }
      }
    }

    @GET
    @Path("redirect")
    void redirect(@Suspended final AsyncResponse asyncResponse) {
      executor.execute {
        controller(REDIRECT) {
          asyncResponse.resume(Response.status(REDIRECT.status).location(new URI(REDIRECT.body)).build())
        }
      }
    }

    @GET
    @Path("error-status")
    void error(@Suspended final AsyncResponse asyncResponse) {
      executor.execute {
        controller(ERROR) {
          asyncResponse.resume(Response.status(ERROR.status).entity(ERROR.body).build())
        }
      }
    }

    @GET
    @Path("exception")
    void exception(@Suspended final AsyncResponse asyncResponse) {
      executor.execute {
        controller(EXCEPTION) {
          def ex = new Exception(EXCEPTION.body)
          asyncResponse.resume(ex)
          throw ex
        }
      }
    }
  }
}
