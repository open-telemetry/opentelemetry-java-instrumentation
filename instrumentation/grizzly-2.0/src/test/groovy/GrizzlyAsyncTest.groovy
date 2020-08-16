/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Response
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig

class GrizzlyAsyncTest extends GrizzlyTest {

  @Override
  HttpServer startServer(int port) {
    ResourceConfig rc = new ResourceConfig()
    rc.register(SimpleExceptionMapper)
    rc.register(AsyncServiceResource)
    GrizzlyHttpServerFactory.createHttpServer(new URI("http://localhost:$port"), rc)
  }

  @Path("/")
  static class AsyncServiceResource {
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
    @Path("query")
    Response query_param(@QueryParam("some") String param, @Suspended AsyncResponse ar) {
      controller(QUERY_PARAM) {
        ar.resume(Response.status(QUERY_PARAM.status).entity("some=$param".toString()).build())
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
    @Path("error-status")
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
}
