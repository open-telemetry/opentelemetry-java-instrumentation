/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizard;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;

class DropwizardAsyncTest extends DropwizardTest {

  @Override
  Class<? extends Application<Configuration>> testApp() {
    return AsyncTestApp.class;
  }

  @Override
  Class<?> testResource() {
    return AsyncServiceResource.class;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    // server spans are ended inside the JAX-RS controller spans
    options.setVerifyServerSpanEndTime(false);
  }

  public static class AsyncTestApp extends Application<Configuration> {
    @Override
    public void initialize(Bootstrap<Configuration> bootstrap) {}

    @Override
    public void run(Configuration configuration, Environment environment) {
      environment.jersey().register(AsyncServiceResource.class);
    }
  }

  @Path("/")
  public static class AsyncServiceResource {
    final ExecutorService executor = Executors.newSingleThreadExecutor();

    @GET
    @Path("success")
    public void success(@Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () ->
              controller(
                  SUCCESS,
                  () ->
                      asyncResponse.resume(
                          Response.status(SUCCESS.getStatus()).entity(SUCCESS.getBody()).build())));
    }

    @GET
    @Path("query")
    public void queryParam(
        @QueryParam("some") String param, @Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () ->
              controller(
                  QUERY_PARAM,
                  () ->
                      asyncResponse.resume(
                          Response.status(QUERY_PARAM.getStatus())
                              .entity("some=" + param)
                              .build())));
    }

    @GET
    @Path("redirect")
    public void redirect(@Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () -> {
            try {
              controller(
                  REDIRECT,
                  () ->
                      asyncResponse.resume(
                          Response.status(REDIRECT.getStatus())
                              .location(new URI(REDIRECT.getBody()))
                              .build()));
            } catch (URISyntaxException e) {
              throw new RuntimeException(e);
            }
          });
    }

    @GET
    @Path("error-status")
    public void error(@Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () ->
              controller(
                  ERROR,
                  () ->
                      asyncResponse.resume(
                          Response.status(ERROR.getStatus()).entity(ERROR.getBody()).build())));
    }

    @GET
    @Path("exception")
    public void exception(@Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () ->
              controller(
                  EXCEPTION,
                  () -> {
                    IllegalStateException ex = new IllegalStateException(EXCEPTION.getBody());
                    asyncResponse.resume(ex);
                    throw ex;
                  }));
    }

    @GET
    @Path("path/{id}/param")
    public void pathParam(@PathParam("id") int param, @Suspended AsyncResponse asyncResponse) {
      executor.execute(
          () ->
              controller(
                  PATH_PARAM,
                  () ->
                      asyncResponse.resume(
                          Response.status(PATH_PARAM.getStatus()).entity(param).build())));
    }

    @GET
    @Path("child")
    public void indexedChild(
        @QueryParam("id") String param, @Suspended AsyncResponse asyncResponse) {
      controller(
          INDEXED_CHILD,
          () -> {
            INDEXED_CHILD.collectSpanAttributes(id -> id.equals("id") ? param : null);
            asyncResponse.resume(
                Response.status(INDEXED_CHILD.getStatus()).entity(INDEXED_CHILD.getBody()).build());
          });
    }

    @GET
    @Path("captureHeaders")
    public void captureHeaders(
        @HeaderParam("X-Test-Request") String header, @Suspended AsyncResponse asyncResponse) {
      controller(
          CAPTURE_HEADERS,
          () ->
              asyncResponse.resume(
                  Response.status(CAPTURE_HEADERS.getStatus())
                      .header("X-Test-Response", header)
                      .entity(CAPTURE_HEADERS.getBody())
                      .build()));
    }
  }
}
