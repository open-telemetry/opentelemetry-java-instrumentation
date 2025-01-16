/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("")
interface ResteasyProxyResource {
  @GET
  @Path("error")
  Response get_error(
      @QueryParam("with") String param,
      @HeaderParam("is-test-server") String isTestServer,
      @HeaderParam("test-request-id") String requestId);

  @GET
  @Path("client-error")
  Response get_client_error(
      @QueryParam("with") String param,
      @HeaderParam("is-test-server") String isTestServer,
      @HeaderParam("test-request-id") String requestId);

  @GET
  @Path("success")
  Response get_success(
      @QueryParam("with") String param,
      @HeaderParam("is-test-server") String isTestServer,
      @HeaderParam("test-request-id") String requestId);

  @POST
  @Path("success")
  Response post_success(
      @QueryParam("with") String param,
      @HeaderParam("is-test-server") String isTestServer,
      @HeaderParam("test-request-id") String requestId);

  @PUT
  @Path("success")
  Response put_success(
      @QueryParam("with") String param,
      @HeaderParam("is-test-server") String isTestServer,
      @HeaderParam("test-request-id") String requestId);
}
