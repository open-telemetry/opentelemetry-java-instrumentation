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

import io.opentelemetry.auto.test.base.HttpServerTest
import org.glassfish.grizzly.http.server.HttpServer
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory
import org.glassfish.jersey.server.ResourceConfig

import javax.ws.rs.GET
import javax.ws.rs.NotFoundException
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class GrizzlyTest extends HttpServerTest<HttpServer> {

  static {
    System.setProperty("ota.integration.grizzly.enabled", "true")
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
