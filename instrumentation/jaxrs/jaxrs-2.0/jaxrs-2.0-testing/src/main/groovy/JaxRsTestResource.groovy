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
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static java.util.concurrent.TimeUnit.SECONDS

import io.opentelemetry.auto.test.base.HttpServerTest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CyclicBarrier
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Application
import javax.ws.rs.core.Response
import javax.ws.rs.ext.ExceptionMapper

@Path("")
class JaxRsTestResource {
  @Path("/success")
  @GET
  String success() {
    HttpServerTest.controller(SUCCESS) {
      SUCCESS.body
    }
  }

  @Path("query")
  @GET
  String query_param(@QueryParam("some") String param) {
    HttpServerTest.controller(QUERY_PARAM) {
      "some=$param"
    }
  }

  @Path("redirect")
  @GET
  Response redirect() {
    HttpServerTest.controller(REDIRECT) {
      Response.status(Response.Status.FOUND)
        .location(new URI(REDIRECT.body))
        .build()
    }
  }

  @Path("error-status")
  @GET
  Response error() {
    HttpServerTest.controller(ERROR) {
      Response.status(ERROR.status)
        .entity(ERROR.body)
        .build()
    }
  }

  @Path("exception")
  @GET
  Object exception() {
    HttpServerTest.controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }

  @Path("path/{id}/param")
  @GET
  String path_param(@PathParam("id") int id) {
    HttpServerTest.controller(PATH_PARAM) {
      id
    }
  }

  static final BARRIER = new CyclicBarrier(2)

  @Path("async")
  @GET
  void asyncOp(@Suspended AsyncResponse response, @QueryParam("action") String action) {
    CompletableFuture.runAsync({
      // await for the test method to verify that there are no spans yet
      BARRIER.await(1, SECONDS)

      switch (action) {
        case "succeed":
          response.resume("success")
          break
        case "throw":
          response.resume(new Exception("failure"))
          break
        case "cancel":
          response.cancel()
          break
        default:
          response.resume(new AssertionError((Object) ("invalid action value: " + action)))
          break
      }
    })
  }

  @Path("async-completion-stage")
  @GET
  CompletionStage<String> jaxRs21Async(@QueryParam("action") String action) {
    def result = new CompletableFuture<String>()
    CompletableFuture.runAsync({
      // await for the test method to verify that there are no spans yet
      BARRIER.await(1, SECONDS)

      switch (action) {
        case "succeed":
          result.complete("success")
          break
        case "throw":
          result.completeExceptionally(new Exception("failure"))
          break
        default:
          result.completeExceptionally(new AssertionError((Object) ("invalid action value: " + action)))
          break
      }
    })
    result
  }
}

class JaxRsTestExceptionMapper implements ExceptionMapper<Exception> {
  @Override
  Response toResponse(Exception exception) {
    return Response.status(500)
      .entity(exception.message)
      .build()
  }
}

class JaxRsTestApplication extends Application {
  @Override
  Set<Class<?>> getClasses() {
    def classes = new HashSet()
    classes.add(JaxRsTestResource)
    classes.add(JaxRsTestExceptionMapper)
    return classes
  }
}