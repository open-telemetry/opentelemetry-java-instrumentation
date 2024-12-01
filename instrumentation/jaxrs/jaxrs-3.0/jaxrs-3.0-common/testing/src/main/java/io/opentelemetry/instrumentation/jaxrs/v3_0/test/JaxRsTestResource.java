/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs.v3_0.test;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.util.concurrent.TimeUnit.SECONDS;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;

@SuppressWarnings("IdentifierName")
@Path("")
public class JaxRsTestResource {
  @Path("/success")
  @GET
  public String success() {
    return controller(SUCCESS, SUCCESS::getBody);
  }

  @Path("query")
  @GET
  public String query_param(@QueryParam("some") String param) {
    return controller(QUERY_PARAM, () -> "some=" + param);
  }

  @Path("redirect")
  @GET
  public Response redirect(@Context UriInfo uriInfo) throws URISyntaxException {
    return controller(
        SUCCESS,
        () ->
            Response.status(Response.Status.FOUND)
                .location(uriInfo.relativize(new URI(REDIRECT.getBody())))
                .build());
  }

  @Path("error-status")
  @GET
  public Response error() {
    return controller(
        SUCCESS, () -> Response.status(ERROR.getStatus()).entity(ERROR.getBody()).build());
  }

  @Path("exception")
  @GET
  public Object exception() {
    return controller(
        SUCCESS,
        () -> {
          throw new IllegalStateException(EXCEPTION.getBody());
        });
  }

  @Path("path/{id}/param")
  @GET
  public String path_param(@PathParam("id") int id) {
    return controller(PATH_PARAM, () -> String.valueOf(id));
  }

  @GET
  @Path("captureHeaders")
  public Response capture_headers(@HeaderParam("X-Test-Request") String header) {
    return controller(
        CAPTURE_HEADERS,
        () ->
            Response.status(CAPTURE_HEADERS.getStatus())
                .header("X-Test-Response", header)
                .entity(CAPTURE_HEADERS.getBody())
                .build());
  }

  @Path("/child")
  @GET
  public void indexed_child(@Context UriInfo uriInfo, @Suspended AsyncResponse response) {
    MultivaluedMap<String, String> parameters = uriInfo.getQueryParameters();

    CompletableFuture.runAsync(
        () ->
            controller(
                INDEXED_CHILD,
                () -> {
                  INDEXED_CHILD.collectSpanAttributes(parameters::getFirst);
                  response.resume("");
                }));
  }

  public static final CyclicBarrier BARRIER = new CyclicBarrier(2);

  @Path("async")
  @GET
  public void asyncOp(@Suspended AsyncResponse response, @QueryParam("action") String action) {
    CompletableFuture.runAsync(
        () -> {
          // await for the test method to verify that there are no spans yet
          try {
            BARRIER.await(10, SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }

          switch (action) {
            case "succeed":
              response.resume("success");
              break;
            case "throw":
              response.resume(new IllegalStateException("failure"));
              break;
            case "cancel":
              response.cancel();
              break;
            default:
              response.resume(new AssertionError("invalid action value: " + action));
              break;
          }
        });
  }

  @Path("async-completion-stage")
  @GET
  public CompletionStage<String> jaxRs21Async(@QueryParam("action") String action) {
    CompletableFuture<String> result = new CompletableFuture<>();
    CompletableFuture.runAsync(
        () -> {
          // await for the test method to verify that there are no spans yet
          try {
            BARRIER.await(10, SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }

          switch (action) {
            case "succeed":
              result.complete("success");
              break;
            case "throw":
              result.completeExceptionally(new IllegalStateException("failure"));
              break;
            default:
              result.completeExceptionally(new AssertionError("invalid action value: " + action));
              break;
          }
        });
    return result;
  }
}
